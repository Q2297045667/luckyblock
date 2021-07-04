package mod.lucky.forge

import mod.lucky.common.gameAPI
import mod.lucky.common.platformAPI
import mod.lucky.forge.game.*
import mod.lucky.java.*
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityClassification
import net.minecraft.entity.EntityType
import net.minecraft.item.crafting.IRecipeSerializer
import net.minecraft.item.crafting.SpecialRecipeSerializer
import net.minecraft.resources.*
import net.minecraft.tileentity.TileEntityType
import net.minecraft.util.ResourceLocation
import net.minecraft.world.gen.GenerationStage
import net.minecraft.world.gen.feature.NoFeatureConfig
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.event.world.BiomeLoadingEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.ForgeRegistries
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object ForgeLuckyRegistry {
    val LOGGER: Logger = LogManager.getLogger()
    val luckyBlock = LuckyBlock()
    val addonLuckyBlocks = HashMap<String, LuckyBlock>()
    val luckyBlockItem = LuckyBlockItem(luckyBlock)
    val luckyBow = LuckyBow()
    val luckySword = LuckySword()
    val luckyPotion = LuckyPotion()
    lateinit var modVersion: String
    lateinit var luckyBlockEntity: TileEntityType<LuckyBlockEntity>
    lateinit var luckyProjectile: EntityType<LuckyProjectile>
    lateinit var thrownLuckyPotion: EntityType<ThrownLuckyPotion>
    lateinit var delayedDrop: EntityType<DelayedDrop>
    lateinit var luckModifierCraftingRecipe: IRecipeSerializer<LuckModifierCraftingRecipe>
    lateinit var addonCraftingRecipe: IRecipeSerializer<AddonCraftingRecipe>
}

private fun getAddonBlock(id: String): LuckyBlock {
    return ForgeLuckyRegistry.addonLuckyBlocks.getOrElse(id, {
        val block = LuckyBlock()
        ForgeLuckyRegistry.addonLuckyBlocks[id] = block
        block
    })
}

@Mod("lucky")
class ForgeMod {
    init {
        platformAPI = JavaPlatformAPI
        gameAPI = ForgeGameAPI
        javaGameAPI = ForgeJavaGameAPI

        ForgeLuckyRegistry.modVersion = ModLoadingContext.get().activeContainer.modInfo.version.toString()

        ForgeGameAPI.init()
        JavaLuckyRegistry.init()

        FMLJavaModLoadingContext.get().modEventBus
            .addListener { _: FMLCommonSetupEvent -> setupCommon() }
        FMLJavaModLoadingContext.get().modEventBus
            .addListener{ _: FMLClientSetupEvent -> setupClient() }
        MinecraftForge.EVENT_BUS.addListener { event: BiomeLoadingEvent ->
            registerBiomeFeatures(event)
        }
        MinecraftForge.EVENT_BUS.register(this)
    }

    private fun registerBiomeFeatures(event: BiomeLoadingEvent) {
        val blockIds = listOf(JavaLuckyRegistry.blockId) + JavaLuckyRegistry.addons.mapNotNull { it.ids.block }
        blockIds.forEach {
            val feature = LuckyWorldFeature(NoFeatureConfig.CODEC, it)
            val configuredFeature = feature.configured(NoFeatureConfig.INSTANCE)
            event.generation.getFeatures(GenerationStage.Decoration.SURFACE_STRUCTURES).add { configuredFeature }
        }
    }

    private fun setupCommon() {
        registerAddonCraftingRecipes()
    }

    @OnlyInClient
    private fun setupClient() {
        MinecraftForge.EVENT_BUS.addListener { _: WorldEvent.Load ->
            JavaLuckyRegistry.notificationState = checkForUpdates(JavaLuckyRegistry.notificationState)
        }

        for (addon in JavaLuckyRegistry.addons) {
            val file = addon.file
            val pack = if (file.isDirectory) FolderPack(file) else FilePack(file)
            val resourceManager = Minecraft.getInstance().resourceManager
            if (resourceManager is SimpleReloadableResourceManager) {
                resourceManager.add(pack)
            }
        }

        registerLuckyBowModels(ForgeLuckyRegistry.luckyBow)
        JavaLuckyRegistry.addons.map { addon ->
            if (addon.ids.bow != null) registerLuckyBowModels(ForgeRegistries.ITEMS.getValue(MCIdentifier(addon.ids.bow!!)) as LuckyBow)
        }

        RenderingRegistry.registerEntityRenderingHandler(ForgeLuckyRegistry.luckyProjectile, ::LuckyProjectileRenderer)
        RenderingRegistry.registerEntityRenderingHandler(ForgeLuckyRegistry.thrownLuckyPotion, ::ThrownLuckyPotionRenderer)
        RenderingRegistry.registerEntityRenderingHandler(ForgeLuckyRegistry.delayedDrop, ::DelayedDropRenderer)
    }
}

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object ForgeSubscriber {
    @JvmStatic @SubscribeEvent
    fun registerBlocks(event: RegistryEvent.Register<MCBlock>) {
        event.registry.register(ForgeLuckyRegistry.luckyBlock.setRegistryName(JavaLuckyRegistry.blockId))
        JavaLuckyRegistry.addons.map { addon ->
            if (addon.ids.block != null) {
                event.registry.register(getAddonBlock(addon.ids.block!!).setRegistryName(addon.ids.block))
            }
        }
    }

    @JvmStatic @SubscribeEvent
    fun registerItems(event: RegistryEvent.Register<MCItem>) {
        event.registry.register(ForgeLuckyRegistry.luckyBlockItem.setRegistryName(JavaLuckyRegistry.blockId))
        event.registry.register(ForgeLuckyRegistry.luckySword.setRegistryName(JavaLuckyRegistry.swordId))
        event.registry.register(ForgeLuckyRegistry.luckyBow.setRegistryName(JavaLuckyRegistry.bowId))
        event.registry.register(ForgeLuckyRegistry.luckyPotion.setRegistryName(JavaLuckyRegistry.potionId))

        JavaLuckyRegistry.addons.map { addon ->
            if (addon.ids.block != null) {
                val block = ForgeLuckyRegistry.addonLuckyBlocks.getOrElse(addon.ids.block!!, { LuckyBlock() })
                event.registry.register(LuckyBlockItem(block).setRegistryName(block.registryName))
            }
            if (addon.ids.sword != null) event.registry.register(LuckySword().setRegistryName(addon.ids.sword))
            if (addon.ids.bow != null) event.registry.register(LuckyBow().setRegistryName(addon.ids.bow))
            if (addon.ids.potion != null) event.registry.register(LuckyPotion().setRegistryName(addon.ids.potion))
        }
    }

    @JvmStatic @SubscribeEvent
    fun registerEntites(event: RegistryEvent.Register<EntityType<*>>) {
        ForgeLuckyRegistry.luckyProjectile = EntityType.Builder.of(::LuckyProjectile, EntityClassification.MISC)
            .setTrackingRange(100)
            .setUpdateInterval(20)
            .setShouldReceiveVelocityUpdates(true)
            .build(JavaLuckyRegistry.projectileId)

        ForgeLuckyRegistry.thrownLuckyPotion = EntityType.Builder.of(::ThrownLuckyPotion, EntityClassification.MISC)
            .setTrackingRange(100)
            .setUpdateInterval(20)
            .setShouldReceiveVelocityUpdates(true)
            .build(JavaLuckyRegistry.potionId)

        ForgeLuckyRegistry.delayedDrop = EntityType.Builder.of(::DelayedDrop, EntityClassification.MISC)
            .setTrackingRange(100)
            .setUpdateInterval(20)
            .setShouldReceiveVelocityUpdates(true)
            .build(JavaLuckyRegistry.potionId)

        event.registry.register(ForgeLuckyRegistry.luckyProjectile.setRegistryName(JavaLuckyRegistry.projectileId))
        event.registry.register(ForgeLuckyRegistry.thrownLuckyPotion.setRegistryName(JavaLuckyRegistry.potionId))
        event.registry.register(ForgeLuckyRegistry.delayedDrop.setRegistryName(JavaLuckyRegistry.delayedDropId))
    }

    @JvmStatic @SubscribeEvent
    fun registerRecipes(event: RegistryEvent.Register<IRecipeSerializer<*>>) {
        ForgeLuckyRegistry.luckModifierCraftingRecipe = SpecialRecipeSerializer(::LuckModifierCraftingRecipe)
        ForgeLuckyRegistry.addonCraftingRecipe = SpecialRecipeSerializer(::AddonCraftingRecipe)

        event.registry.register(ForgeLuckyRegistry.luckModifierCraftingRecipe.setRegistryName(ResourceLocation("lucky:crafting_luck")))
        event.registry.register(ForgeLuckyRegistry.addonCraftingRecipe.setRegistryName(ResourceLocation("lucky:crafting_addons")))
    }

    @JvmStatic @SubscribeEvent
    fun registerTileEntites(event: RegistryEvent.Register<TileEntityType<*>>) {
        val validBlocks = listOf(ForgeLuckyRegistry.luckyBlock) + JavaLuckyRegistry.addons
            .mapNotNull { it.ids.block }
            .map { getAddonBlock(it) }

        ForgeLuckyRegistry.luckyBlockEntity = @Suppress TileEntityType.Builder.of(::LuckyBlockEntity, *validBlocks.toTypedArray()).build(null)

        event.registry.register(ForgeLuckyRegistry.luckyBlockEntity.setRegistryName(JavaLuckyRegistry.blockId))
    }
}
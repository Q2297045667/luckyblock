@file:OptIn(ExperimentalCli::class)

package mod.lucky.tools

import mod.lucky.tools.generateBedrockConfig.GenerateBedrockConfig
import kotlinx.cli.*

fun main(args: Array<String>) {
    val parser = ArgParser("luckytools", strictSubcommandOptionsOrder = true)

    parser.subcommands(GenerateBedrockConfig(), NbtToMcstructure(), DownloadBlockIds())

    parser.parse(args)
}

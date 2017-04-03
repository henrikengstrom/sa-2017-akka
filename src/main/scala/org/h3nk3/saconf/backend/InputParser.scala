package org.h3nk3.saconf.backend

import scala.util.parsing.combinator.RegexParsers

trait InputParser {
  sealed trait Cmd
  object Cmd {
    case object Help extends Cmd
    case object Exit extends Cmd
    case object Initiate extends Cmd
    case object Stop extends Cmd
    case class Unknown(s: String) extends Cmd
    def apply(cmd: String): Cmd = {
      CmdParser.parse(cmd)
    }
  }

  object CmdParser extends RegexParsers {
    def parse(s: String): Cmd =
      parseAll(parser, s) match {
        case Success(cmd, _) => cmd
        case _ => Cmd.Unknown(s)
      }

    def help: Parser[Cmd.Help.type] = "help|h".r ^^ (_ => Cmd.Help)
    def exit: Parser[Cmd.Exit.type] = "exit|e".r ^^ (_ => Cmd.Exit)
    def initiate: Parser[Cmd.Initiate.type] = "init|i".r ^^ (_ => Cmd.Initiate)
    def stop: Parser[Cmd.Stop.type] = "stop|s".r ^^ (_ => Cmd.Stop)
  }

  private val parser: CmdParser.Parser[Cmd] =
    CmdParser.stop | CmdParser.initiate | CmdParser.exit | CmdParser.help
}

package com.ldaniels528.verify

import com.ldaniels528.verify.command.{UnixLikeArgs, Command, CommandParser, UnixLikeParams}
import org.scalatest.Matchers._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FeatureSpec, GivenWhenThen}

/**
 * Verify Runtime Context Specification
 * @author Lawrence Daniels <lawrence.daniels@gmail.com>
 */
class VxRuntimeContextSpec() extends FeatureSpec with GivenWhenThen with MockitoSugar {

  info("As a Runtime Context")
  info("I want to be able to parse, validate and execute module commands")

  feature("Unix-Like Command Parameter Argument Verification") {
    scenario("The Command Parameter instance should parse and verify command line input into arguments") {
      Given("A Command Parameter Set and command line input")
      val paramSet = UnixLikeParams(defaults = Seq("key" -> false), flags = Seq("-r" -> "recursive"))
      val commandLineInput = "zrm -r /some/path/to/delete"
      val command = mock[Command]

      When("Parsing command line input into arguments")
      val args = CommandParser.parse(commandLineInput)

      Then("The arguments should be successfully verified")
      val params = args.tail
      //paramSet.checkArgs(command, params)
      paramSet.transform(params) shouldBe UnixLikeArgs(Nil, Map("-r" -> Some("/some/path/to/delete")))
    }
  }

}

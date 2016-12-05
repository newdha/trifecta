package com.github.ldaniels528.trifecta.messages.query

import com.github.ldaniels528.commons.helpers.OptionHelper._
import com.github.ldaniels528.trifecta.TxRuntimeContext
import com.github.ldaniels528.trifecta.io.IOCounter
import com.github.ldaniels528.trifecta.messages.codec.json.JsonMessageDecoder
import com.github.ldaniels528.trifecta.messages.codec.{MessageCodecFactory, MessageDecoder}
import com.github.ldaniels528.trifecta.messages.logic.ConditionCompiler._
import com.github.ldaniels528.trifecta.messages.logic.Expressions.Expression
import com.github.ldaniels528.trifecta.messages.{MessageInputSource, MessageOutputSource}

import scala.concurrent.{ExecutionContext, Future}

/**
  * KQL Selection Query
  * @author lawrence.daniels@gmail.com
  */
case class KQLSelection(source: IOSource,
                        destination: Option[IOSource] = None,
                        fields: Seq[String],
                        criteria: Option[Expression],
                        restrictions: KQLRestrictions,
                        limit: Option[Int])
  extends KQLQuery {

  /**
    * Executes the given query
    * @param rt      the given [[TxRuntimeContext runtime context]]
    * @param counter the given [[IOCounter I/O Counter]]
    */
  override def executeQuery(rt: TxRuntimeContext, counter: IOCounter)(implicit ec: ExecutionContext): Future[KQLResult] = {
    // get the input source and its decoder
    val inputSource: Option[MessageInputSource] = rt.getInputHandler(rt.getDeviceURLWithDefault("topic", source.deviceURL))
    val inputDecoder: Option[MessageDecoder[_]] = source.decoderURL match {
      case None | Some("default") =>
        val topic = source.deviceURL.split("[:]").last
        rt.lookupDecoderByName(topic) ?? Some(JsonMessageDecoder)
      case Some(decoderURL) =>
        rt.lookupDecoderByName(decoderURL) ?? MessageCodecFactory.getDecoder(rt.config, decoderURL)
    }

    // get the output source and its encoder
    val outputSource: Option[MessageOutputSource] = destination.flatMap(src => rt.getOutputHandler(src.deviceURL))
    val outputDecoder: Option[MessageDecoder[_]] = for {
      dest <- destination; url <- dest.decoderURL
      decoder <- MessageCodecFactory.getDecoder(rt.config, url)
    } yield decoder

    // compile conditions & get all other properties
    val conditions = criteria.map(compile(_, inputDecoder)).toSeq
    val maximum = limit ?? Some(25)

    // perform the query/copy operation
    if (outputSource.nonEmpty) throw new IllegalStateException("Insert is not yet supported")
    else {
      val querySource = inputSource.flatMap(_.getQuerySource).orDie(s"No query compatible source found for URL '${source.deviceURL}'")
      val decoder = inputDecoder.orDie(s"No decoder found for URL ${source.decoderURL}")
      querySource.findMany(fields, decoder, conditions, restrictions, maximum, counter)
    }
  }

  /**
    * Returns the string representation of the query
    * @example select symbol, exchange, lastTrade, open, close, high, low from "shocktrade.quotes.avro" via "avro:file:avro/quotes.avsc" where lastTrade <= 1 and volume >= 1,000,000
    * @example select strategy, groupedBy, vip, site, qName, srcIP, frequency from "dns.query.topHitters" via "avro:file:avro/topTalkers.avsc" where strategy == "IPv4-CMS" and groupedBy == "vip,site" limit 35
    * @example select strategy, groupedBy, vip, site, qName, srcIP, frequency from "dns.query.topHitters" via "avro:file:avro/topTalkers.avsc" where strategy == "IPv4-CMS"
    * @return the string representation
    */
  override def toString: String = {
    val sb = new StringBuilder(s"select ${fields.mkString(", ")} from $source")
    destination.foreach(dest => sb.append(s" into $dest"))
    if (criteria.nonEmpty) {
      sb.append(" where ")
      sb.append(criteria.map(_.toString) mkString " ")
    }
    sb.append(" ").append(restrictions)
    limit.foreach(count => sb.append(s" limit $count"))
    sb.toString()
  }

}
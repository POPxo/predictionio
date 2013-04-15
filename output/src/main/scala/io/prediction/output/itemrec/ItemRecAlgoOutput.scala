package io.prediction.output.itemrec

import io.prediction.commons.Config
import io.prediction.commons.modeldata.ItemRecScore
import io.prediction.commons.settings.{Algo, App, Engine, OfflineEval}

import scala.util.Random

trait ItemRecAlgoOutput {
  def output(uid: String, n: Int, itypes: Option[List[String]], after: Option[ItemRecScore])(implicit app: App, algo: Algo, offlineEval: Option[OfflineEval]): Seq[ItemRecScore]
}

object ItemRecAlgoOutput {
  val config = new Config
  val items = config.getAppdataItems
  val u2iActions = config.getAppdataU2IActions

  def output(uid: String, n: Int, itypes: Option[List[String]])(implicit app: App, engine: Engine, algo: Algo, offlineEval: Option[OfflineEval] = None): Seq[String] = {
    /** Serendipity settings. */
    val serendipity = engine.settings.get("serendipity") map { _.asInstanceOf[Int] }

    /** Serendipity value (s) from 0-10 in engine settings.
      * Implemented as randomly picking items from top n*(s+1) results.
      */
    val finalN = serendipity map { s => n*(s+1) } getOrElse n

    /** Seen item filter settings. */
    val unseenonly = engine.settings.get("unseenonly") map { _.asInstanceOf[Boolean] } getOrElse false

    /** Filter seen item.
      * Query U2I appdata with UID and IIDs.
      * If the result is non-zero, substract it from the current output.
      */
    var outputBuffer = collection.mutable.ListBuffer[String]()

    if (unseenonly) {
      var stopMore = false
      var after: Option[ItemRecScore] = None

      while (outputBuffer.length < finalN && !stopMore) {
        val moreItemRecScores = more(uid, finalN, itypes, after)
        val moreIids = moreItemRecScores.map(_.iid).toList

        /** Stop the loop if no more scores can be found. */
        if (moreItemRecScores.length == 0)
          stopMore = true
        else {
          val seenItems = u2iActions.getAllByAppidAndUidAndIids(app.id, uid, moreIids)
          outputBuffer ++= (moreIids filterNot (seenItems.toList.map(_.iid).contains))
          after = Some(moreItemRecScores.last)
        }
      }
    } else outputBuffer ++= more(uid, finalN, itypes, None) map { _.iid }

    /** At this point "output" is guaranteed to have n*(s+1) items (seen or unseen) unless model data is exhausted. */
    val output = outputBuffer.toList.take(finalN)

    /** Serendipity output. */
    val serendipityOutput = serendipity map { s =>
      if (s > 0)
        Random.shuffle(output).take(n)
      else
        output
    } getOrElse output

    /** Freshness (0 <= f <= 10) is implemented as the ratio of final results being top N results re-sorted by start time.
      * E.g. For f = 4, 40% of the final output will consist of top N results re-sorted by start time.
      */
    val freshness = engine.settings.get("freshness") map { _.asInstanceOf[Int] }

    /** Freshness output. */
    val finalOutput = freshness map { f =>
      if (f > 0) {
        val freshnessN = scala.math.round(n*f/10)
        val otherN = n-freshnessN
        val freshnessOutput = items.getRecentByIds(app.id, output).map(_.id)
        freshnessOutput.take(freshnessN) ++ serendipityOutput.take(otherN)
      } else
        serendipityOutput
    } getOrElse serendipityOutput

    finalOutput
  }

  /** Private method just to get items. */
  private def more(uid: String, n: Int, itypes: Option[List[String]], after: Option[ItemRecScore] = None)(implicit app: App, algo: Algo, offlineEval: Option[OfflineEval]): Seq[ItemRecScore] = {
    algo.infoid match {
      case "pdio-knnitembased" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "pdio-randomrank" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "pdio-latestrank" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-alswr" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-itembased" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-knnuserbased" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-parallelals" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-slopeone" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-svdplusplus" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-svdsgd" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case "mahout-thresholduserbased" => knnitembased.ItemRecKNNItemBasedAlgoOutput.output(uid, n, itypes, after)
      case _ => throw new RuntimeException("Unsupported itemrec algorithm package: %s" format algo.infoid)
    }
  }
}

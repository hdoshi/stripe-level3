package com.stripe.ctf.instantcodesearch
import com.twitter.util.{Future, Promise, FuturePool}
import com.twitter.concurrent.Broker
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpResponseStatus}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.io.Source
import scala.collection.concurrent.TrieMap

class SearchServer(port : Int, id : Int) extends AbstractSearchServer(port, id) {
  val IndexPath = "instantcodesearch-" + id + ".index"
  case class Query(q : String, broker : Broker[SearchResult])
  lazy val searcher = new Searcher(IndexPath)
  @volatile var indexed = false

  override def healthcheck() = {
    Future.value(successResponse())
  }

  override def isIndexed() = {
    if (indexed) {
      Future.value(successResponse())
    }
    else {
      Future.value(errorResponse(HttpResponseStatus.OK, "Not indexed"))
    }
  }

  override def index(path: String) = {
    val indexer = new Indexer(path, id)

    FuturePool.unboundedPool {
      System.err.println("[node #" + id + "] Indexing path: " + path)
      indexer.index()
      System.err.println("[node #" + id + "] Waiting to index: " + IndexPath)
      indexer.waitForAll
      System.err.println("[node #" + id + "] Writing index to: " + IndexPath)
      indexed = true
      indexer.write(IndexPath)
    }

    Future.value(successResponse())
  }

  override def query(q: String) = {
    System.err.println("[node #" + id + "] Searching for: " + q)
    handleSearch(q)
  }


  def handleSearch(q: String) = {

    val promise = Promise[HttpResponse]
    if (SearchServer.dict.contains(q)) {
      val f = SearchServer.dict.get(q)
      System.err.println("Dictionary "+q)
      f match {
        case Some(x) => promise.setValue(querySuccessResponse(x))
        case None => promise.setValue(querySuccessResponse(List[Match]()))
      }
    }
    else {
      val searches = new Broker[Query]()
      searches.recv foreach { q =>
        FuturePool.unboundedPool {searcher.search(q.q, q.broker)}
      }

      val matches = new Broker[SearchResult]()
      val err = new Broker[Throwable]
      searches ! new Query(q, matches)

      var results = List[Match]()

      matches.recv foreach { m =>
        m match {
          case m : Match => results = m :: results
          case Done() => {
            SearchServer.dict.put(q, results)
            promise.setValue(querySuccessResponse(results))
          }
        }
      }
    }
    promise
  }
}

object SearchServer {
  val dict = new ConcurrentHashMap[String, List[Match]].asScala
}

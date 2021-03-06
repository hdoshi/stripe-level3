package com.stripe.ctf.instantcodesearch

import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.collection.immutable.StringOps
import java.util.Date;
class SearchMasterServer(port: Int, id: Int) extends AbstractSearchServer(port, id) {
  val NumNodes = 3
  def this(port: Int) { this(port, 0) }

  val start = (new Date()).getTime()
  val clients = (1 to NumNodes)
    .map { id => new SearchServerClient(port + id, id)}
    .toArray

  val localSearch = new SearchServer(port + NumNodes + 1, NumNodes + 1)

  override def isIndexed() = {

    if (localSearch.isLocalIndexed) {
      Future.value(successResponse())
    }
    else {
      Future.value(errorResponse(HttpResponseStatus.OK, "Nodes are not indexed"))
    }
    /*
    val responsesF = Future.collect(clients.map {client => client.isIndexed()})
    val successF = responsesF.map {responses => responses.forall { response =>

        (response.getStatus() == HttpResponseStatus.OK
          && response.getContent.toString(UTF_8).contains("true"))
      }
    }

    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        if ((new Date()).getTime() - start > 200 * 1000)
          successResponse()
        else
          errorResponse(HttpResponseStatus.OK, "Nodes are not indexed")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.OK, "Nodes are not indexed")
      )
    }
    */
  }

  override def healthcheck() = {
    val responsesF = Future.collect(clients.map {client => client.healthcheck()})
    val successF = responsesF.map {responses => responses.forall { response =>
        response.getStatus() == HttpResponseStatus.OK
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      )
    }
  }

  override def index(path: String) = {
    System.err.println(
      "[master] Requesting " + NumNodes + " nodes to index path: " + path
    )

    //val responses = Future.collect(clients.map {client => client.index(path)})
    localSearch.index(path)
    //responses.map {_ => successResponse()}
  }

  override def query(q: String) = {
    localSearch.query(q)
    //val index:Int = q.length % NumNodes
    //val responses = clients.map {client => client.query(q)}
    //val response = clients(index).query(q)
    //responses(0)
    //response
  }
}

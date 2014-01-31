package com.stripe.ctf.instantcodesearch

import java.io._
import java.util.Arrays
import java.nio.file._
import java.nio.charset._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.io.Source
import com.twitter.util.{Future, Promise, FuturePool, Await}


class Indexer(indexPath: String, id: Int) {
  val root = FileSystems.getDefault().getPath(indexPath)
  val idx = new Index(root.toAbsolutePath.toString)
  var myFutures: List[Future[Int]] = List[Future[Int]]()

  def index() : Indexer = {
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir : Path, attrs : BasicFileAttributes) : FileVisitResult = {
        if (Files.isHidden(dir) && dir.toString != ".")
          return FileVisitResult.SKIP_SUBTREE
        return FileVisitResult.CONTINUE
      }
      override def visitFile(file : Path, attrs : BasicFileAttributes) : FileVisitResult = {
        if (Files.isHidden(file))
          return FileVisitResult.CONTINUE
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
          return FileVisitResult.CONTINUE
        if (Files.size(file) > (1 << 20))
          return FileVisitResult.CONTINUE
        val bytes = Files.readAllBytes(file)
        if (Arrays.asList(bytes).indexOf(0) > 0)
          return FileVisitResult.CONTINUE
        val decoder = Charset.forName("UTF-8").newDecoder()
        decoder onMalformedInput CodingErrorAction.REPORT
        decoder onUnmappableCharacter CodingErrorAction.REPORT
        try {
          val r = new InputStreamReader(new ByteArrayInputStream(bytes), decoder)
          val strContents:String = slurp(r)
          val f = dict_index(strContents, file, id)
          myFutures = f :: myFutures
          idx.addFile(root.relativize(file).toString, strContents)
        } catch {
          case e: IOException => {
            return FileVisitResult.CONTINUE
          }
        }

        return FileVisitResult.CONTINUE
      }
    })
    return this
  }

  def waitForAll = {
    println("Wait over "+ Await.result(Future.collect(myFutures).map(_.sum)))
  }

  def dict_index(strContents:String, file:Path, id:Int):Future[Int] = {
    var promise = Promise[Int]
    FuturePool.unboundedPool {
      for(needle <- Source.fromFile("/usr/share/dict/words").getLines()) {
        if (needle.length > 4 && ((needle.length % 3) == (id - 1)) && strContents.contains(needle)) {
          var line = 0
          strContents.split("\n").zipWithIndex.
            filter { case (l,n) => l.contains(needle) }.
            map { case (l,n) => {
              if (!SearchServer.dict.contains(needle)) {
                SearchServer.dict(needle) = List[Match]()
              }
              SearchServer.dict(needle) ::= new Match(root.relativize(file).toString, n+1)
            }
          }
        }
      }
      System.out.println("Done with " + file)
      promise.setValue(1)
    }
    promise
  }

  def write(path: String) = {
    idx.write(new File(path))
  }
}

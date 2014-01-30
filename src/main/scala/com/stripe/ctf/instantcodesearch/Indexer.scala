package com.stripe.ctf.instantcodesearch

import java.io._
import java.util.Arrays
import java.nio.file._
import java.nio.charset._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.io.Source


class Indexer(indexPath: String, id: Int) {
  val root = FileSystems.getDefault().getPath(indexPath)
  val idx = new Index(root.toAbsolutePath.toString)

  val dict = new ConcurrentHashMap[String, List[Match]].asScala
  def dictionary_words() = {
    for(line <- Source.fromFile("/usr/share/dict/words").getLines()) {
      //dict.put(line, List[Match]())
      //System.err.println(dict.toString)
      if (line.length % 3 == id - 1) {
        //handleSearch_dict(line)
        dict.put(line, List[Match]())
      }
      //System.err.println("Dictionary search "+line)
    }
  }


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

          //System.err.println("Doing "+ file + " " + id)
          var i:Int = 0
          for(needle <- Source.fromFile("/usr/share/dict/words").getLines()) {

            if (needle.length > 4 && i % 4 == 0 && ((needle.length % 3) == (id - 1)) && strContents.contains(needle)) {
              var line = 0
              //System.out.printf(needle + id)
              strContents.split("\n").zipWithIndex.
                filter { case (l,n) => l.contains(needle) }.
                map { case (l,n) => {

                    if (!SearchServer.dict.contains(needle)) {
                        SearchServer.dict(needle) = List[Match]()
                    }
                    SearchServer.dict(needle) ::= new Match(root.relativize(file).toString, n+1)
                    //System.out.println("Result " + n + " " + file + " " + needle)
                  }
                }

            }
            i = i + 1
          }
          //System.err.println("Done with " + file)
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

  def dictionary:scala.collection.concurrent.Map[String, List[Match]] = {
    return dict
  }

  def write(path: String) = {
    idx.write(new File(path))
  }
}

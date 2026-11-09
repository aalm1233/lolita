import com.lolita.app.data.square.*
import com.google.gson.JsonParser
import java.io.File
fun main(args:Array<String>) {
 val root=File(args[0]); val old=SquarePackage.decode(File(root,"catalog.json").readText(),network=true)
 val pages=JsonParser.parseString(File(root,"pages.json").readText()).asJsonArray
 for(mode in listOf("original","restore_event_descriptions","fresh")) {
  var merged:SquareCatalog?=if(mode=="fresh")null else old
  if(mode=="restore_event_descriptions") {
   val events=pages.flatMap{it.asJsonObject["changes"].asJsonArray.toList()}.flatMap{SquarePackage.decode(it.asJsonObject["catalog"].toString(),true).events}.associateBy{it.id}
   merged=old.copy(events=old.events.map { e ->
    val n=events[e.id]
    if(n==null)e else e.copy(subtitle=n.subtitle,state=n.state,date=n.date,price=n.price,priceNote=n.priceNote,note=n.note,material=n.material,detail=n.detail,captured=n.captured)
   })
  }
  var seq=0L
  try {
   for(page in pages)for(row in page.asJsonObject["changes"].asJsonArray) {
    seq=row.asJsonObject["seq"].asLong
    merged=SquarePackage.merge(merged,SquarePackage.decode(row.asJsonObject["catalog"].toString(),true),network=true)
   }
   println("$mode passed through $seq: ${merged!!.products.size} products")
  } catch(e:IllegalArgumentException) { println("$mode failed at $seq: ${e.message}") }
 }
}
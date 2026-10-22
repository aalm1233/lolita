package com.lolita.app.ui.theme

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lolita.app.data.MaterialTestFixture
import com.lolita.app.data.categoryItems
import com.lolita.app.ui.screen.common.*
import com.lolita.app.ui.screen.item.*
import com.lolita.app.ui.theme.skin.animation.ObserveSkinScroll
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Actual providers/components at native device resolution, using isolated original vector pictures. */
class TypographyScreenTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var f:MaterialTestFixture
    private lateinit var cards:List<ItemCardData>
    private val skin=mutableStateOf(SkinType.DEFAULT)
    private val dark=mutableStateOf(false)
    private val page=mutableStateOf("scale")
    private val filterCount=mutableStateOf(3)
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var host:android.view.View
    private var submitted=0
    private val records=JSONArray()
    private val mixed="API 配置 · ¥12,345.67 · 尺码 M / L"
    @Before fun setup(){
        f=MaterialTestFixture();runBlocking{f.init();cards=f.categoryItems().take(2).map{ItemCardData(it,"Classic 参考品牌",null,"服装",268.5,true)}}
        compose.setContent{
            val view=LocalView.current
            SideEffect{host=view;(view.context as android.app.Activity).window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)}
            val configuration=Configuration(LocalConfiguration.current).apply{uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides configuration){LolitaTheme(skin.value,dark.value){key(skin.value,dark.value,page.value){
                var input by remember{mutableStateOf("")}
                val scroll=rememberLazyListState();ObserveSkinScroll(scroll)
                val pager=rememberPagerState(initialPage=1,pageCount={4})
                val focus=remember{FocusRequester()}
                Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),topBar={
                    if(page.value in listOf("home","search")) Surface(color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.statusBarsPadding()){
                        if(page.value=="home")NormalModeBar(pager,{}, {},false,{},filterCount.value,SortOption.entries.first(),true,true,{},{},IconKey.GridView)
                        else SearchModeBar(input,{input=it},{page.value="home"},focus,"搜索物品")
                    }} else GradientTopAppBar(title={Text("衣橱与试衣",Modifier.testTag("typeAppBar"))},navigationIcon={IconButton(onClick={}){SkinIcon(IconKey.ArrowBack)}})
                },bottomBar={Surface{Column(Modifier.fillMaxWidth().imePadding().padding(16.dp)){
                    Button(onClick={submitted++},Modifier.fillMaxWidth().testTag("typePrimary")){Text("保存本次资料")}
                }}}){padding->LazyColumn(state=scroll,modifier=Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("typeList"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    if(page.value in listOf("home","search")){
                        item{SectionHeader("服饰与小物")}
                        item{ItemCard(cards[0],{},{},{})}
                        item{Row(Modifier.testTag("typeGrid"),horizontalArrangement=Arrangement.spacedBy(8.dp)){cards.forEach{card->ItemGridCard(card,{},{},{},Modifier.weight(1f))}}}
                    }else{
                        item{LolitaCard(variant=CardVariant.FEATURED){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("统一整理我的衣橱",style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("typeFeature"))
                            Text("参考照片、确认展示图与日常试衣，共用清楚一致的文字层级。",style=MaterialTheme.typography.bodyMedium)
                        }}}
                        item{LolitaSection("服饰资料"){row{
                            Text("蕾丝领衬衫",style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("typeRow"))
                            Text(mixed,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.testTag("typeReading"))
                            Text("确认后可用于试衣，原商品照片继续保留。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("typeHelp"))
                            Text("单件 · 已拥有",style=MaterialTheme.typography.metadata,modifier=Modifier.testTag("typeMetadata"))
                        }}}
                        item{OutlinedTextField(input,{input=it},label={Text("物品名称")},placeholder={Text("填写名称后继续核对")},modifier=Modifier.fillMaxWidth().testTag("typeInput"))}
                        item{LolitaSection("当前订单"){row{Text("¥12,345.67",style=MaterialTheme.typography.titleLarge.numericReading,modifier=Modifier.testTag("typeAmount"));Text("金额按原值显示，字体风格不改变资料。",style=MaterialTheme.typography.bodySmall)}}}
                    }
                }}
            }}}
        }
    }
    @After fun close(){runBlocking{f.close()}}
    private fun layout(node:SemanticsNodeInteraction):TextLayoutResult{val result=mutableListOf<TextLayoutResult>();node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(result)};return result.single()}
    private fun complete(node:SemanticsNodeInteraction){
        node.assertIsDisplayed();val result=layout(node)
        // A cached MultiParagraph can retain the max constraint width even when Text
        // wraps its node to the actual line width. Check rendered line edges, not blank width.
        val lines=(0 until result.lineCount).map { line ->
            JSONObject().put("left",result.getLineLeft(line).toDouble()).put("right",result.getLineRight(line).toDouble())
                .put("bottom",result.getLineBottom(line).toDouble()).put("end",result.getLineEnd(line)).put("ellipsized",result.isLineEllipsized(line))
        }
        val clipped=result.getLineEnd(result.lineCount-1)<result.layoutInput.text.length || (0 until result.lineCount).any { line ->
            result.isLineEllipsized(line) || result.getLineLeft(line)<-1f ||
                result.getLineRight(line)>result.size.width+1f || result.getLineBottom(line)>result.size.height+1f
        }
        if(clipped){
            capture("failure-${skin.value}-${page.value}")
            File(context.filesDir,"typography-native/failure.json").writeText(JSONObject()
                .put("text",result.layoutInput.text.text).put("size",result.size.toString())
                .put("paragraph_width",result.multiParagraph.width.toDouble()).put("lines",JSONArray(lines)).toString(2))
        }
        assertFalse("Text clipped: ${result.layoutInput.text}; size=${result.size}; lines=$lines",clipped)
    }

    private fun checkFilterCounts(prefix:String) {
        for(count in listOf(0,3,12)) {
            compose.runOnIdle{filterCount.value=count}
            for(label in listOf("位置","服饰","套装","心愿"))complete(compose.onNode(hasText(label) and hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)), useUnmergedTree = true))
            val badge=compose.onNodeWithTag("homeFilterBadge")
            if(count==0)badge.assertDoesNotExist() else {
                badge.assertIsDisplayed()
                complete(compose.onNode(hasText("$count") and hasAnyAncestor(hasTestTag("homeFilterBadge")),useUnmergedTree=true))
                val badgeBounds=badge.getUnclippedBoundsInRoot()
                val buttonBounds=compose.onNodeWithTag("homeFilterButton").getUnclippedBoundsInRoot()
                assertTrue("Filter lower half must remain clear",badgeBounds.bottom<=buttonBounds.top+(buttonBounds.bottom-buttonBounds.top)/2f)
                assertTrue("Badge stays inside filter width",badgeBounds.left>=buttonBounds.left && badgeBounds.right<=buttonBounds.right)
            }
            capture("$prefix-filter-$count")
        }
        compose.runOnIdle{filterCount.value=3}
    }
    private fun capture(name:String){
        compose.waitForIdle();val dir=File(context.filesDir,"typography-native").apply{mkdirs()}
        compose.onRoot().captureToImage().asAndroidBitmap().let{im->File(dir,"$name.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
    }
    @Test fun native2kHasRealHierarchyAndStableReadingAcrossSevenSkins(){
        Assume.assumeTrue(context.resources.configuration.fontScale<1.5f)
        assertEquals(1440,context.resources.displayMetrics.widthPixels)
        assertEquals(600,context.resources.displayMetrics.densityDpi)
        var readingLines:List<Int>?=null
        for(night in listOf(false,true))for(type in SkinType.entries){
            compose.runOnIdle{skin.value=type;dark.value=night;page.value="scale"}
            val nodes=listOf("typeFeature","typeAppBar","typeRow","typeHelp","typeMetadata").associateWith{compose.onNodeWithTag(it)}
            nodes.values.forEach(::complete)
            complete(compose.onNodeWithText("服饰资料"))
            val styles=nodes.mapValues{layout(it.value).layoutInput.style}
            val section=layout(compose.onNodeWithText("服饰资料")).layoutInput.style
            assertTrue(styles.getValue("typeFeature").fontSize>styles.getValue("typeAppBar").fontSize)
            assertEquals(section.fontSize,styles.getValue("typeAppBar").fontSize)
            assertTrue(styles.getValue("typeAppBar").fontWeight!!<section.fontWeight!!)
            assertTrue(section.fontSize>styles.getValue("typeRow").fontSize)
            assertTrue(styles.getValue("typeRow").fontSize>styles.getValue("typeHelp").fontSize)
            val reading=layout(compose.onNodeWithTag("typeReading"))
            assertEquals(FontFamily.Default,reading.layoutInput.style.fontFamily)
            val lines=(0 until reading.lineCount).map{reading.getLineEnd(it)}
            readingLines?.let{assertEquals(it,lines)};readingLines=lines
            assertEquals(0f,reading.layoutInput.style.letterSpacing.value)
            val button=layout(compose.onNodeWithText("保存本次资料"));assertEquals(FontFamily.Default,button.layoutInput.style.fontFamily);complete(compose.onNodeWithTag("typePrimary"))
            capture("scale-${type.name.lowercase()}-${if(night)"dark" else "light"}")
            records.put(JSONObject().put("skin",type.name).put("dark",night).put("sizes",JSONObject(styles.mapValues{it.value.fontSize.value})).put("section",section.fontSize.value).put("body_line_ends",JSONArray(lines)))
            compose.runOnIdle{page.value="home"}
            for(label in listOf("位置","服饰","套装","心愿"))complete(compose.onNode(hasText(label) and hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)), useUnmergedTree = true))
            compose.onNodeWithTag("typeList").performScrollToNode(hasTestTag("typeGrid"))
            capture("home-${type.name.lowercase()}-${if(night)"dark" else "light"}")
            if(type==SkinType.DEFAULT && !night)checkFilterCounts("normal")
        }
        File(context.filesDir,"typography-native/measurements.json").writeText(JSONObject().put("width_px",1440).put("density_dpi",600).put("width_dp",context.resources.configuration.screenWidthDp).put("font_scale",context.resources.configuration.fontScale.toDouble()).put("actual_styles",records).toString(2))
    }
    @Test fun realLargeFontKeepsCompleteControlsAndKeyboardActions(){
        Assume.assumeTrue(context.resources.configuration.fontScale>=1.99f && context.resources.configuration.screenWidthDp<=320)
        complete(compose.onNodeWithTag("typePrimary"));complete(compose.onNodeWithTag("typeAppBar"))
        compose.onNodeWithTag("typeList").performScrollToNode(hasTestTag("typeInput"))
        compose.onNodeWithTag("typeInput").performClick().performTextInput("Classic 衬衫")
        compose.waitUntil(10_000){androidx.core.view.ViewCompat.getRootWindowInsets(host)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        compose.onNodeWithTag("typeInput").performScrollTo().assertIsDisplayed().assertTextContains("Classic 衬衫")
        complete(compose.onNodeWithTag("typePrimary"))
        val dir=File(context.filesDir,"typography-native").apply{mkdirs()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{im->File(dir,"small-input-ime.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
        compose.onNodeWithTag("typePrimary").performClick();assertEquals(1,submitted)
        compose.runOnIdle{page.value="home"}
        for(label in listOf("位置","服饰","套装","心愿"))complete(compose.onNode(hasText(label) and hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)), useUnmergedTree = true))
        checkFilterCounts("small")
        capture("small-home")
        compose.runOnIdle{page.value="search"}
        compose.onNode(hasSetTextAction()).performClick().performTextInput("蕾丝")
        compose.waitUntil(10_000){androidx.core.view.ViewCompat.getRootWindowInsets(host)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        complete(compose.onNodeWithText("取消"))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{im->File(dir,"small-search-ime.png").outputStream().use{im.compress(Bitmap.CompressFormat.PNG,100,it)};im.recycle()}
    }
}

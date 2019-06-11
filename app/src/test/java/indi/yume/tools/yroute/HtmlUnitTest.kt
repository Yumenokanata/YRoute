package indi.yume.tools.yroute

import com.gargoylesoftware.htmlunit.BrowserVersion
import com.gargoylesoftware.htmlunit.Page
import com.gargoylesoftware.htmlunit.ProxyConfig
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput
import com.gargoylesoftware.htmlunit.html.HtmlTextInput
import org.junit.Test
import net.sourceforge.htmlunit.corejs.javascript.tools.shell.Global.quit
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.htmlunit.HtmlUnitDriver


class HtmlUnitTest {
//    @Test
    fun htmlUnit1Test() {
        val url = "https://github.com/login"

        val webclient = WebClient(BrowserVersion.CHROME)
        webclient.options.isUseInsecureSSL = true
        webclient.options.isCssEnabled = false
        webclient.options.isRedirectEnabled = true
        webclient.options.isJavaScriptEnabled = true
        webclient.options.isThrowExceptionOnScriptError = false
        webclient.options.isThrowExceptionOnFailingStatusCode = false
        webclient.cookieManager.isCookiesEnabled = true
//        webclient.options.proxyConfig = ProxyConfig("192.168.100.253", 10086, true)

        val htmlpage = webclient.getPage<HtmlPage>(url)
        val form = htmlpage.forms.first { it.methodAttribute == "post" }
        val cisUidTextField = form.getInputByName<HtmlTextInput>("login")
        val passwordTextField = form.getInputByName<HtmlPasswordInput>("password")
        val button = form.getInputByName<HtmlSubmitInput>("commit")
        cisUidTextField.valueAttribute = "Yumenokanata"
        passwordTextField.valueAttribute = "RksL0430"

//        htmlpage.executeJavaScript("submitbutton('login','loginForm');")
        webclient.waitForBackgroundJavaScript(2000)
        button.focus()
        val page = button.click<Page>()
        webclient.waitForBackgroundJavaScript(7000)


//        val result = nextPage.asText()
        println(webclient.currentWindow.enclosedPage)
    }

//    @Test
    fun htmlUnit2Test() {
        val url = "http://www.google.com"

        val webclient = WebClient(BrowserVersion.CHROME)
        webclient.options.isUseInsecureSSL = true
        webclient.options.isCssEnabled = false
        webclient.options.isRedirectEnabled = true
        webclient.options.isJavaScriptEnabled = true
        webclient.options.isThrowExceptionOnScriptError = false
        webclient.options.isThrowExceptionOnFailingStatusCode = false
        webclient.cookieManager.isCookiesEnabled = true
//        webclient.options.proxyConfig = ProxyConfig("192.168.100.253", 10086, true)

        val htmlpage = webclient.getPage<HtmlPage>(url)
        val element = htmlpage.getElementByName<HtmlTextInput>("q")

        element.valueAttribute = "Cheese!"
        element.type("\n")

        println("Page title is: ${webclient.currentWindow.enclosedPage}")
    }

//    @Test
    fun html2Test() {
//        val driver = HtmlUnitDriver(BrowserVersion.BEST_SUPPORTED, true)
        val driver = FirefoxDriver()

            // And now use this jump visit Google
        driver.get("http://www.google.com")
        // Alternatively the same thing can be done like this
        // driver.navigate().jump("http://www.google.com");

        // Find the text input element by its name
        val element = driver.findElement(By.name("q"))

        // Enter something jump search for
        element.sendKeys("Cheese!")

        // Now submit the form. WebDriver will find the form for us from the element
        element.submit()

        // Check the title of the page
        println("Page title is: " + driver.title)

        // Google's search is rendered dynamically with JavaScript.
        // Wait for the page jump load, timeout after 10 seconds
        WebDriverWait(driver, 10).until(ExpectedCondition { d -> d!!.title.toLowerCase().startsWith("cheese!") })

        // Should see: "cheese! - Google Search"
        println("Page title is: " + driver.title)

        //Close the browser
        driver.quit()
    }
}
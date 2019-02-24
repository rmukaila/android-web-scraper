package com.daandtu.webscraper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import static java.lang.Thread.sleep;

@SuppressLint("SetJavaScriptEnabled,unused,Deprecation")
public class WebScraper {

    private Context context;
    private WebView web;

    private String URL;
    private String userAgent;

    private PageLoadedListener pageLoadedListener;
    private WaitForHtml HtmlListener;
    private List<Task> tasks = new ArrayList<>();

    public static int MAX = -1;

    public WebScraper(final Context context) {
        this.context = context;
        web = new WebView(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw();
        }
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setBlockNetworkImage(true);
        web.getSettings().setLoadsImagesAutomatically(false);
        JSInterface jInterface = new JSInterface(context);
        web.addJavascriptInterface(jInterface, "HtmlViewer");
        userAgent = web.getSettings().getUserAgentString();
        web.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                Log.i("Webscraper",url);
                if (pageLoadedListener != null){
                    URL = url;
                    pageLoadedListener.loaded(url);
                    pageLoadedListener = null;
                }
                web.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                web.layout(0, 0, web.getMeasuredWidth(), web.getMeasuredHeight());
                //noinspection deprecation
                web.setDrawingCacheEnabled(true);
            }
        });
    }
    public View getView() {
        return web;
    }



    private class JSInterface {
        private Context ctx;
        JSInterface(Context ctx) {
            this.ctx = ctx;
        }
        @JavascriptInterface
        public void showHTML(String html) {
            if (HtmlListener != null){
                HtmlListener.gotHtml(html);
            }
        }
    }

    //Web tasks
    public void execute(WebTaskListener webTaskListener){
        Handler handler = new Handler();
        final int[] count = {0};
        final Dictionary<String, String> result = new Hashtable<>();
        Log.i("Webscraper", "Starting execution");
        TaskListener taskListener = () -> {
            Log.i("Webscraper", "Task Nr.: " + String.valueOf(count[0]) );
            if (count[0] < tasks.size()-1) {
                count[0]++;
                handler.post(tasks.get(count[0]));
            }else{
                webTaskListener.finished(result);
            }
        };
        for (Task task: tasks) task.set(taskListener, result);
        handler.post(tasks.get(0));
    }
    protected void addTask(String javascript, boolean storeResult, String key){
        tasks.add(new Task() {
            @Override
            public void run() {
                web.evaluateJavascript(javascript, s -> {
                    if (storeResult) result.put(key, s.substring(1, s.length() - 1));
                    taskListener.done();
                });
            }
        });
    }
    protected void addTask(String javascript){
        addTask(javascript, false, null);
    }
    public void waitForElement(Element element, int timeout){
        String js = "javascript:document.body.contains(" + element.getElement() + ")";
        for (int i = 0; i < timeout/300; i++){
            tasks.add(new Task() {
                @Override
                public void run() {
                    web.evaluateJavascript(js, s -> {
                        if (s.equals("false")){
                            waitTime(300);
                        }
                        taskListener.done();
                    });
                }
            });
        }
    }
    public void log(String text){
        tasks.add(new Task() {
            @Override
            public void run() {
                Log.i("Webscraper",text);
                taskListener.done();
            }
        });
    }
    public void waitForAttribute(Element element, String attribute, String value, int timeout){
        String js = "javascript:" + element.getElement() + "." + attribute + ";";
        for (int i = 0; i < timeout/300; i++){
            tasks.add(new Task() {
                @Override
                public void run() {
                    web.evaluateJavascript(js, s -> {
                        if (!s.equals(value)){
                            waitTime(300);
                        }
                        taskListener.done();
                    });
                }
            });
        }
    }
    public void typeText(int... keyevents){
        tasks.add(new Task() {
            @Override
            public void run() {
                BaseInputConnection inputConnection = new BaseInputConnection(getView(),true);
                for (int i: keyevents){
                    inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,i));
                    inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,i));
                }
                waitTime(keyevents.length * 40);
                taskListener.done();
            }
        });
    }
    public void typeText(String text){
        int[] keys = new int[text.length()];
        for (int i = 0; i < text.length(); i++){
            keys[i] = KEYCODES(text.toLowerCase().charAt(i));
        }
        typeText(keys);
    }

    public void waitTime(int millis){
        tasks.add(new Task() {
            @Override
            public void run() {
                waitTime(millis);
                taskListener.done();
            }
        });
    }
    public void waitForPage(){
        tasks.add(new Task() {
            @Override
            public void run() {
                pageLoadedListener = url -> taskListener.done();
            }
        });
    }
    public void loadURL(String Url){
        tasks.add(new Task() {
            @Override
            public void run() {
                web.loadUrl(Url);
                pageLoadedListener = url -> taskListener.done();
            }
        });
    }
    public void reload(){
        tasks.add(new Task() {
            @Override
            public void run() {
                web.reload();
                pageLoadedListener = url -> taskListener.done();
            }
        });
    }
    public void reload(PageLoadedListener listener) {
        web.reload();
        pageLoadedListener = listener;
    }
    public void getHtml(WaitForHtml htmlListener) {
        //web.evaluateJavascript("(function(){return document.getElementsByTagName('html')[0].innerHTML})();", htmlListener::gotHtml);
        web.evaluateJavascript("javascript:window.HtmlViewer.showHTML(document.getElementsByTagName('html')[0].innerHTML);", null);
        this.HtmlListener = htmlListener;
    }
    public void loadURL(String URL, PageLoadedListener listener) {
        this.URL = URL;
        web.loadUrl(URL);
        pageLoadedListener = listener;
    }

    //Get webview attributes
    public String getWebsiteTitle(){
        return web.getTitle();
    }
    public String getURL() {
        return web.getUrl();
    }
    public Bitmap getFavicon(){
        return web.getFavicon();
    }
    public SslCertificate getSslCertificate(){
        return web.getCertificate();
    }

    //Setup
    public void setUserAgentToDesktop(boolean desktop){
        if (desktop){
            String osString = userAgent.substring(userAgent.indexOf("("), userAgent.indexOf(")") + 1);
            web.getSettings().setUserAgentString(userAgent.replace(osString,"(X11; Linux x86_64)"));
        }else{
            web.getSettings().setUserAgentString(userAgent);
        }
    }
    public void setLoadImages(boolean enabled) {
        web.getSettings().setBlockNetworkImage(!enabled);
        web.getSettings().setLoadsImagesAutomatically(enabled);
    }

    //Screenshots
    public Bitmap takeScreenshot() { //Pay attention with big webpages
        return takeScreenshot(MAX, MAX);
    }
    public Bitmap takeScreenshot(int width, int height) {
        try {
            if (width < 0 || height < 0) {
                web.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            }
            if (width < 0) {
                width = web.getMeasuredWidth();
            }
            if (height < 0) {
                height = web.getMeasuredHeight();
            }
            web.layout(0, 0, width, height);
            //noinspection deprecation
            web.setDrawingCacheEnabled(true);
            try {
                sleep(30);
            } catch (InterruptedException ignored) {}
            //noinspection deprecation
            return Bitmap.createBitmap(web.getDrawingCache());
        } catch (NullPointerException ignored) {
            return null;
        }
    }
    public int getMaxHeight() {
        web.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return web.getMeasuredHeight();
    }
    public int getMaxWidth() {
        web.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return web.getMeasuredWidth();
    }

    //Browser data
    public void clearHistory() {
        web.clearHistory();
    }
    public void clearCache() {
        web.clearCache(true);
    }
    public void clearCookies(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }else{
            CookieSyncManager cookieSync = CookieSyncManager.createInstance(context);
            cookieSync.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSync.stopSync();
            cookieSync.sync();
        }
    }
    public void clearAll(){
        clearHistory();
        clearCache();
        clearCookies();
    }

    //FindWebViewElement
    public Element findElementsByClassName(String classname, int id){
        return new Element(this, Element.CLASS, classname, id);
    }
    public Element findElementByClassName(String classname){
        return findElementsByName(classname, 0);
    }
    public Element findElementById(String id){
        return new Element(this, Element.ID, id, 0);
    }
    public Element findElementsByName(String name, int id){
        return new Element(this, Element.NAME, name,id);
    }
    public Element findElementByName(String name){
        return findElementsByName(name,0);
    }
    public Element findElementByXpath(String xpath){
        return new Element(this, Element.XPATH, xpath,0);
    }
    public Element findElementByCustomJavascript(String javascript){
        return new Element(this, Element.CUSTOM, javascript, 0);
    }
    public Element findElementsByAttribute(String attribute, String value, int count){
        return new Element(this, Element.ATTRIBUTE, attribute + "=" + value,count);
    }
    public Element findElementByAttribute(String attribute, String value){
        return findElementsByAttribute(attribute, value, 0);
    }
    public Element findElementsByValue(String value, int id){
        return new Element(this,Element.VALUE, value, id);
    }
    public Element findElementByInnerHtml(String innerHtml, String type){
        return new Element(this, Element.HTML, "//" + type + "[contains(text(), '" + innerHtml +"')]", 0);
    }
    public Element findElementByValue(String value){
        return findElementsByValue(value,0);
    }
    public Element findElementsByTitle(String title, int id){
        return new Element(this, Element.TITLE,title, id);
    }
    public Element findElementByTitle(String title){
        return findElementsByTitle(title,0);
    }
    public Element findElementsByTagName(String tagName, int id){
        return new Element(this,Element.TAG,tagName, id);
    }
    public Element findElementByTagName(String tagName){
        return findElementsByTagName(tagName,0);
    }
    public Element findElementsByType(String type, int id){
        return new Element(this, Element.TYPE, type, id);
    }
    public Element findElementByType(String type){
        return findElementsByType(type,0);
    }

    public interface PageLoadedListener{
        void loaded(String url);
    }
    public interface WaitForHtml{
        void gotHtml(String html);
    }
    public interface WebTaskListener{
        void finished(Dictionary<String, String> result);
    }
    private interface TaskListener{
        void done();
    }

    private abstract  class Task implements Runnable{
        TaskListener taskListener;
        Dictionary<String,String> result;
        public void set(TaskListener taskListener, Dictionary<String, String> result){
            this.taskListener = taskListener;
            this.result = result;
        }
        public void waitTime(int time){
            try {
                sleep(time);
            } catch (InterruptedException ignored) {}
        }
    }

    private static int KEYCODES(char ch){
        switch (ch) {
            case 'a': return KeyEvent.KEYCODE_A;
            case 'b': return KeyEvent.KEYCODE_B;
            case 'c': return KeyEvent.KEYCODE_C;
            case 'd': return KeyEvent.KEYCODE_D;
            case 'e': return KeyEvent.KEYCODE_E;
            case 'f': return KeyEvent.KEYCODE_F;
            case 'g': return KeyEvent.KEYCODE_G;
            case 'h': return KeyEvent.KEYCODE_H;
            case 'i': return KeyEvent.KEYCODE_I;
            case 'j': return KeyEvent.KEYCODE_J;
            case 'k': return KeyEvent.KEYCODE_K;
            case 'l': return KeyEvent.KEYCODE_L;
            case 'm': return KeyEvent.KEYCODE_M;
            case 'n': return KeyEvent.KEYCODE_N;
            case 'o': return KeyEvent.KEYCODE_O;
            case 'p': return KeyEvent.KEYCODE_P;
            case 'q': return KeyEvent.KEYCODE_Q;
            case 'r': return KeyEvent.KEYCODE_R;
            case 's': return KeyEvent.KEYCODE_S;
            case 't': return KeyEvent.KEYCODE_T;
            case 'u': return KeyEvent.KEYCODE_U;
            case 'v': return KeyEvent.KEYCODE_V;
            case 'w': return KeyEvent.KEYCODE_W;
            case 'x': return KeyEvent.KEYCODE_X;
            case 'y': return KeyEvent.KEYCODE_Y;
            case 'z': return KeyEvent.KEYCODE_Z;
            case '0': return KeyEvent.KEYCODE_0;
            case '1': return KeyEvent.KEYCODE_1;
            case '2': return KeyEvent.KEYCODE_2;
            case '3': return KeyEvent.KEYCODE_3;
            case '4': return KeyEvent.KEYCODE_4;
            case '5': return KeyEvent.KEYCODE_5;
            case '6': return KeyEvent.KEYCODE_6;
            case '7': return KeyEvent.KEYCODE_7;
            case '8': return KeyEvent.KEYCODE_8;
            case '9': return KeyEvent.KEYCODE_9;
            case '@': return KeyEvent.KEYCODE_AT;
            case '-': return KeyEvent.KEYCODE_MINUS;
            case '/': return KeyEvent.KEYCODE_SLASH;
            case '.': return KeyEvent.KEYCODE_PERIOD;
            case ',': return KeyEvent.KEYCODE_COMMA;
            case ';': return KeyEvent.KEYCODE_SEMICOLON;
            case ' ': return KeyEvent.KEYCODE_SPACE;
            default: return 0;
        }
    }
}
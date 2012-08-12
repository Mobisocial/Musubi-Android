package mobisocial.musubi.webapp;

import java.util.Arrays;

import mobisocial.musubi.webapp.SocialKitJavascript.DataUri;
import mobisocial.test.TestBase;

public class SocialKitJsTest extends TestBase {

    public void testBase64Uri() {
        String data = "data:image/gif;base64,R0lGODdhMAAwAPAAAAAAAP///ywAAAAAMAAw" + 
        "AAAC8IyPqcvt3wCcDkiLc7C0qwyGHhSWpjQu5yqmCYsapyuvUUlvONmOZtfzgFz" +
        "ByTB10QgxOR0TqBQejhRNzOfkVJ+5YiUqrXF5Y5lKh/DeuNcP5yLWGsEbtLiOSp" +
        "a/TPg7JpJHxyendzWTBfX0cxOnKPjgBzi4diinWGdkF8kjdfnycQZXZeYGejmJl" +
        "ZeGl9i2icVqaNVailT6F5iJ90m6mvuTS4OK05M0vDk0Q4XUtwvKOzrcd3iq9uis" +
        "F81M1OIcR7lEewwcLp7tuNNkM3uNna3F2JQFo97Vriy/Xl4/f1cf5VWzXyym7PH" +
        "hhx4dbgYKAAA7";
        DataUri uri = new DataUri(data);
        assertEquals("image/gif", uri.mimeType);
        assertNull(uri.parameters);
    }

    public void testUrlEncodedUri() {
        String data = "data:,A%20brief%20note";
        DataUri uri = new DataUri(data);
        assertEquals(uri.mimeType, "text/plain");
        assertEquals("charset=US-ASCII", uri.parameters);
        assertTrue(Arrays.equals("A brief note".getBytes(), uri.data));
    }

    public void testUrlParseQuery() {
        String data = "data:application/vnd-xxx-query,select_vcount,fcol_from_fieldtable/local";
        DataUri uri = new DataUri(data);
        assertEquals("application/vnd-xxx-query", uri.mimeType);
        assert(Arrays.equals("select_vcount,fcol_from_fieldtable/local".getBytes(), uri.data));
    }
}

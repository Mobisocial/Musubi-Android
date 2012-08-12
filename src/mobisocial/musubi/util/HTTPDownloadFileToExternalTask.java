/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.util;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;
import android.os.Environment;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import android.os.AsyncTask;
import android.util.Log;

public class HTTPDownloadFileToExternalTask extends AsyncTask<String, Void, String> {
    @Override
    public String doInBackground(String... urls) {
        for (String url : urls) {
        	FileOutputStream out = null;
        	InputStream in = null;
            try {
                URL apkUrl = new URL(url);
                HttpURLConnection c = (HttpURLConnection) apkUrl.openConnection();
                c.setRequestMethod("GET");
                c.setDoOutput(true);
                c.connect();

                String PATH = Environment.getExternalStorageDirectory() + "/download/";
                File file = new File(PATH);
                file.mkdirs();
                File outputFile = new File(file, "app.apk");
                out = new FileOutputStream(outputFile);

                in = c.getInputStream();

                byte[] buffer = new byte[1024];
                int len1 = 0;
                while ((len1 = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len1);
                }
                return outputFile.getPath();
            } catch (IOException e) {
            } finally {
            	try {
    				if(in != null) in.close();
    				if(out != null) out.close();
    			} catch (IOException e) {
    				Log.e("HTTPDownloadtoexternal", "failed to close streams for http external", e);
    			}
            }
        }
        return null;
    }
}
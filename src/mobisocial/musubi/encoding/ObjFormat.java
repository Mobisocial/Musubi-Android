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

package mobisocial.musubi.encoding;

import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.socialkit.Obj;

/**
 * Container class for data parsed from or prepared for the network.
 * 
 * {@see ObjectMapper}
 */
public class ObjFormat {
    public FeedType feedType;
    public byte[] feedCapability;
    public String appId;
    public long timestamp;
    public String type;
    public String jsonSrc;
    public byte[] raw;
    public Integer intKey;
    public String stringKey;

    public ObjFormat(FeedType feedType, byte[] feedCapability, String appId,
            long timestamp, Obj data) {
        this.appId = appId;
        this.feedType = feedType;
        this.feedCapability = feedCapability;
        this.timestamp = timestamp;
        this.type = data.getType();
        if (data.getJson() != null) {
            this.jsonSrc = data.getJson().toString();
        }
        this.raw = data.getRaw();
        this.intKey = data.getIntKey();
        this.stringKey = data.getStringKey();
    }

    public ObjFormat() {
    }
}

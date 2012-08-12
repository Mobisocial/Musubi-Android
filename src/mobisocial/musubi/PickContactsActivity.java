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

package mobisocial.musubi;
import mobisocial.musubi.ui.MusubiBaseActivity;

/**
 * Pick contacts and/or groups for various purposes.
 * TODO: This may not work as you intended.
 */
public class PickContactsActivity extends MusubiBaseActivity {
    public static final String EXTRA_CONTACTS = "contacts";
    public static final String INTENT_EXTRA_PARENT_FEED = "feed";
    public static final String EXTRA_FEEDS = "feeds";
    public static final String INTENT_EXTRA_MEMBERS_MAX = "max";
}
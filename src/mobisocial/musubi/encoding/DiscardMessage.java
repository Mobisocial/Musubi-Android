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

import android.util.Base64;
import mobisocial.musubi.model.MDevice;

public class DiscardMessage extends Exception {
	
	public static class InvalidAuthority extends DiscardMessage {
		public InvalidAuthority() {
			super("Message not allowed to be x-mited using local authority");
		}
	}
	public static class NotToMe extends DiscardMessage {
		public NotToMe(String msg) {
			super(msg);
		}
	}
	public static class Duplicate extends DiscardMessage {
		public final MDevice mFrom;
		public Duplicate(MDevice from, byte[] hash) {
			super("duplicate message hash=" + Base64.encodeToString(hash, Base64.DEFAULT));
			mFrom = from;
		}
	}
	public static class Blacklist extends DiscardMessage {
		public Blacklist(String msg) {
			super(msg);
		}
	}
	public static class Corrupted extends DiscardMessage {
		public Corrupted(String msg) {
			super(msg);
		}
		public Corrupted(String msg, Throwable t) {
			super(msg, t);
		}
	}
	public static class BadSignature extends DiscardMessage {
		public BadSignature(String msg) {
			super(msg);
		}
	}
	public static class BadObjFormat extends DiscardMessage {
        public BadObjFormat(String msg) {
            super(msg);
        }
        public BadObjFormat(String msg, Throwable t) {
            super(msg, t);
        }
    }
	public DiscardMessage(String msg) {
		super(msg);
	}
	public DiscardMessage(String msg, Throwable t) {
		super(msg, t);
	}
}

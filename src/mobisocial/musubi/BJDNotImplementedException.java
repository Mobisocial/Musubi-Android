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

/**
 * Temporary exception so we can clear all compile errors and revisit the code later.
 *
 */
public class BJDNotImplementedException extends RuntimeException {
    private static final long serialVersionUID = 10203939446489L;

    public static final String MSG_LOCAL_PERSON_ID = "App.instance().getLocalPersonId() was here";

    private BJDNotImplementedException(String msg) {
        super(msg);
    }

    /**
     * Trick eclipse into thinking code beyond the exception is reachable.
     */
    @Deprecated
    public static void except(String msg) {
        throw new BJDNotImplementedException(msg);
    }
}

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

package mobisocial.musubi.model.helpers;

import gnu.trove.iterator.TLongIterator;
import mobisocial.musubi.provider.MusubiContentProvider;

public class SQLClauseHelper {
    public static String andClauses(String A, String B) {
        if(A == null && B == null) return "1 = 1";
        if(A == null) return B;
        if(B == null) return A;
        StringBuilder sql = new StringBuilder(A.length() + B.length() + 10);
        return sql.append("(").append(A).append(") AND (").append(B).append(")").toString();
    }

    public static String[] andArguments(String[] A, String[] B) {
        if (A == null) return B;
        if (B == null) return A;
        String[] C = new String[A.length + B.length];
        System.arraycopy(A, 0, C, 0, A.length);
        System.arraycopy(B, 0, C, A.length, B.length);
        return C;
    }

    public static String[] andArguments(String[] A, Object... B) {
        if (B == null) return A;

        int aLen = (A == null) ? 0 : A.length;
        int bLen = (B == null) ? 0 : B.length;        
        String[] C = new String[aLen + bLen];
        if (aLen > 0) {
            System.arraycopy(A, 0, C, 0, A.length);
        }
        for (int i = 0; i < B.length; i++) {
            C[aLen + i] = B[i].toString();
        }
        return C;
    }

    public static void appendArray(StringBuilder sb, TLongIterator i) {
    	sb.append("(");
    	if(i.hasNext()) {
    		sb.append(i.next());
    	}
    	while(i.hasNext()) {
    		sb.append(',');
    		sb.append(i.next());
    	}
    	sb.append(")");
    }

    public static String appOrUnknown(String realAppId) {
        return new StringBuffer("('").append(realAppId).append("','")
                .append(MusubiContentProvider.UNKNOWN_APP_ID).append("')").toString();
    }
}

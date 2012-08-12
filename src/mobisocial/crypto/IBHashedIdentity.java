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

package mobisocial.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

public class IBHashedIdentity 
{
    public enum Authority 
    {
        //don't change the order, it maps to #s
        Email,
        PhoneNumber,
        OpenID,
        Twitter,
        Facebook, 
        Local, //wiz feed/musippi
        Stanford
    }
    public final byte[] identity_;
    public final Authority authority_;
    public final byte[] hashed_;
    public final long temporalFrame_;
    public IBHashedIdentity(Authority authority, byte[] hashed, long temporalFrame)
    {
        //message format will use sha256 ids
        assert(hashed.length == 32);
        try {
            authority_ = authority;
            hashed_ = hashed;
            temporalFrame_ = temporalFrame;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bo);
            o.writeByte(authority.ordinal());
            o.write(hashed_, 0, hashed_.length);
            o.writeLong(temporalFrame);
            o.flush();
            identity_ = bo.toByteArray();
        } catch(IOException e) {
            throw new RuntimeException("failed to serialize identity", e);
        }
    }
    public IBHashedIdentity(byte[] raw) throws CorruptIdentity
    {
        try {
            identity_ = raw;
            DataInputStream i = new DataInputStream(new ByteArrayInputStream(raw));
            authority_ = Authority.values()[i.readByte()];
            hashed_ = new byte[32];
            i.read(hashed_, 0, hashed_.length);
            temporalFrame_ = i.readLong();
        } catch(IOException e) {
            throw new RuntimeException("failed to unserialize identity", e);
        } catch(ArrayIndexOutOfBoundsException e) {
        	throw new CorruptIdentity("identity had an unknown type", e);
        }
    }
    
    @Override
    public boolean equals(Object o) {
		if(!IBHashedIdentity.class.isInstance(o))
			return false;
		IBHashedIdentity other = (IBHashedIdentity)o;
		return Arrays.equals(identity_, other.identity_);
    }
    @Override
    public int hashCode() {
    	return Arrays.hashCode(identity_);
    }
    public boolean equalsStable(IBHashedIdentity o) {
		IBHashedIdentity other = (IBHashedIdentity)o;
		return authority_ == other.authority_ && Arrays.equals(hashed_, other.hashed_);
    }
	public IBHashedIdentity at(int temporalFrame) {
		return new IBHashedIdentity(authority_, hashed_, temporalFrame);
	}

	@Override
	public String toString() {
	    return "[IBHashed " + authority_ + " / " + new BigInteger(hashed_).toString(16) + "]";
	}
}
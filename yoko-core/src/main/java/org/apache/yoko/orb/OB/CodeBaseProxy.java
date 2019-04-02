/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.yoko.orb.OB;

import org.apache.yoko.orb.CORBA.InputStream;
import org.apache.yoko.orb.OCI.Buffer;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.ORB;
import org.omg.CORBA.Repository;
import org.omg.CORBA.ValueDefPackage.FullValueDescription;
import org.omg.CORBA.portable.ObjectImpl;
import org.omg.IOP.SendingContextRunTime;
import org.omg.IOP.ServiceContext;
import org.omg.SendingContext.CodeBase;
import org.omg.SendingContext.CodeBaseHelper;

import static org.apache.yoko.orb.OB.MinorCodes.MinorInvalidContextID;
import static org.apache.yoko.orb.OB.MinorCodes.describeBadParam;
import static org.omg.CORBA.CompletionStatus.COMPLETED_NO;

public class CodeBaseProxy extends LocalObject implements CodeBase {

    final ORBInstance orbInstance_;
    ServiceContext ctx;
    CodeBase codebase;

    CodeBaseProxy(ORBInstance orb, ServiceContext ctx) {

        if (ctx.context_id != SendingContextRunTime.value) {
            throw new BAD_PARAM(describeBadParam(MinorInvalidContextID), MinorInvalidContextID, COMPLETED_NO);
        }

        this.orbInstance_ = orb;
        this.ctx = ctx;
    }

    @Override
    public String implementation(String arg0) {
        return getCodeBase(orbInstance_).implementation(arg0);
    }

    @Override
    public String[] implementations(String[] arg0) {
        return getCodeBase(orbInstance_).implementations(arg0);
    }

    @Override
    public String[] bases(String arg0) {
        return getCodeBase(orbInstance_).bases(arg0);
    }

    @Override
    public Repository get_ir() {
        return getCodeBase(orbInstance_).get_ir();
    }

    @Override
    public FullValueDescription meta(String arg0) {
        return getCodeBase(orbInstance_).meta(arg0);
    }

    @Override
    public FullValueDescription[] metas(String arg0) {
        return getCodeBase(orbInstance_).metas(arg0);
    }
    
    public CodeBase getCodeBase() {
    	return getCodeBase(orbInstance_);
    }

    
    private CodeBase getCodeBase(ORBInstance orb) {
        
        if (codebase == null || getorb(codebase) != orb.getORB()) {

            Buffer buf = new Buffer(ctx.context_data);
            InputStream in = new InputStream(buf);
            in._OB_ORBInstance(orb);
            in._OB_readEndian();
            org.omg.CORBA.Object obj = in.read_Object();
            try {
                codebase = CodeBaseHelper.narrow(obj);
            } catch (BAD_PARAM ex) {
                codebase = null;
            }

            ctx = null;
        }

        // TODO: add minor code //
        
        
        return codebase;
    }

    /**
     * @param codebase
     * @return
     */
    private ORB getorb(org.omg.CORBA.Object codebase) {
        if (codebase instanceof ObjectImpl) {
            return ((ObjectImpl)codebase)._orb();   
        } else {
            return null;   
        }
    }

}

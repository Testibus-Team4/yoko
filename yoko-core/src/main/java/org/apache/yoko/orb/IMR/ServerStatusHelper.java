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

package org.apache.yoko.orb.IMR;

import org.apache.yoko.util.MinorCodes;

//
// IDL:orb.yoko.apache.org/IMR/ServerStatus:1.0
//
final public class ServerStatusHelper
{
    public static void
    insert(org.omg.CORBA.Any any, ServerStatus val)
    {
        org.omg.CORBA.portable.OutputStream out = any.create_output_stream();
        write(out, val);
        any.read_value(out.create_input_stream(), type());
    }

    public static ServerStatus
    extract(org.omg.CORBA.Any any)
    {
        if(any.type().equivalent(type()))
            return read(any.create_input_stream());
        else
            throw new org.omg.CORBA.BAD_OPERATION(
                MinorCodes
                        .describeBadOperation(MinorCodes.MinorTypeMismatch),
                MinorCodes.MinorTypeMismatch, org.omg.CORBA.CompletionStatus.COMPLETED_NO);
    }

    private static org.omg.CORBA.TypeCode typeCode_;

    public static org.omg.CORBA.TypeCode
    type()
    {
        if(typeCode_ == null)
        {
            org.omg.CORBA.ORB orb = org.omg.CORBA.ORB.init();
            String[] members = new String[5];
            members[0] = "FORKED";
            members[1] = "STARTING";
            members[2] = "RUNNING";
            members[3] = "STOPPING";
            members[4] = "STOPPED";
            typeCode_ = orb.create_enum_tc(id(), "ServerStatus", members);
        }

        return typeCode_;
    }

    public static String
    id()
    {
        return "IDL:orb.yoko.apache.org/IMR/ServerStatus:1.0";
    }

    public static ServerStatus
    read(org.omg.CORBA.portable.InputStream in)
    {
        ServerStatus _ob_v;
        _ob_v = ServerStatus.from_int(in.read_ulong());
        return _ob_v;
    }

    public static void
    write(org.omg.CORBA.portable.OutputStream out, ServerStatus val)
    {
        out.write_ulong(val.value());
    }
}
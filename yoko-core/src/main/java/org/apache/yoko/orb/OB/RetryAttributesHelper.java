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

import org.apache.yoko.util.MinorCodes;

//
// IDL:orb.yoko.apache.org/OB/RetryAttributes:1.0
//
final public class RetryAttributesHelper
{
    public static void
    insert(org.omg.CORBA.Any any, RetryAttributes val)
    {
        org.omg.CORBA.portable.OutputStream out = any.create_output_stream();
        write(out, val);
        any.read_value(out.create_input_stream(), type());
    }

    public static RetryAttributes
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
            org.omg.CORBA.StructMember[] members = new org.omg.CORBA.StructMember[4];

            members[0] = new org.omg.CORBA.StructMember();
            members[0].name = "mode";
            members[0].type = orb.get_primitive_tc(org.omg.CORBA.TCKind.tk_short);

            members[1] = new org.omg.CORBA.StructMember();
            members[1].name = "interval";
            members[1].type = orb.get_primitive_tc(org.omg.CORBA.TCKind.tk_ulong);

            members[2] = new org.omg.CORBA.StructMember();
            members[2].name = "max";
            members[2].type = orb.get_primitive_tc(org.omg.CORBA.TCKind.tk_ulong);

            members[3] = new org.omg.CORBA.StructMember();
            members[3].name = "remote";
            members[3].type = orb.get_primitive_tc(org.omg.CORBA.TCKind.tk_boolean);

            typeCode_ = orb.create_struct_tc(id(), "RetryAttributes", members);
        }

        return typeCode_;
    }

    public static String
    id()
    {
        return "IDL:orb.yoko.apache.org/OB/RetryAttributes:1.0";
    }

    public static RetryAttributes
    read(org.omg.CORBA.portable.InputStream in)
    {
        RetryAttributes _ob_v = new RetryAttributes();
        _ob_v.mode = in.read_short();
        _ob_v.interval = in.read_ulong();
        _ob_v.max = in.read_ulong();
        _ob_v.remote = in.read_boolean();
        return _ob_v;
    }

    public static void
    write(org.omg.CORBA.portable.OutputStream out, RetryAttributes val)
    {
        out.write_short(val.mode);
        out.write_ulong(val.interval);
        out.write_ulong(val.max);
        out.write_boolean(val.remote);
    }
}
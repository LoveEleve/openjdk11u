/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 */

package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.DataAmount;
import jdk.jfr.Name;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "JavaNativeReallocate")
@Label("Java Native Reallocate")
@Category("Java Application")
@Description("Reallocate memory when using native")
public class JavaNativeReallocateEvent extends AbstractJDKEvent {

    public static final ThreadLocal<JavaNativeReallocateEvent> EVENT =
        new ThreadLocal<>() {
            @Override protected JavaNativeReallocateEvent initialValue() {
                return new JavaNativeReallocateEvent();
            }
        };

    @Label("freeAddr")
    public long freeAddr;

    @Label("allocAddr")
    public long allocAddr;

    @Label("Allocation Size")
    @DataAmount
    public long allocationSize;

    public void reset() {
        allocationSize = 0;
        freeAddr = 0;
        allocAddr = 0;
    }
}

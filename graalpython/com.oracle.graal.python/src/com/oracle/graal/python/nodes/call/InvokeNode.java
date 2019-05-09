/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

abstract class AbstractInvokeNode extends Node {

    @CompilationFinal private boolean excSlotInitialized;
    @CompilationFinal private FrameSlot excSlot;
    @CompilationFinal private BranchProfile illegalFrameSlotProfile;

    private final ConditionProfile isClassBodyProfile = ConditionProfile.createBinaryProfile();

    protected static boolean shouldInlineGenerators() {
        return PythonOptions.getOption(PythonLanguage.getContextRef().get(), PythonOptions.ForceInlineGeneratorCalls);
    }

    @TruffleBoundary
    protected static RootCallTarget getCallTarget(Object callee) {
        RootCallTarget callTarget;
        Object actualCallee = callee;
        if (actualCallee instanceof PFunction) {
            callTarget = ((PFunction) actualCallee).getCallTarget();
        } else if (actualCallee instanceof PBuiltinFunction) {
            callTarget = ((PBuiltinFunction) callee).getCallTarget();
        } else {
            throw new UnsupportedOperationException("Unsupported callee type " + actualCallee);
        }
        return callTarget;
    }

    protected final void optionallySetClassBodySpecial(Object[] arguments, CallTarget callTarget) {
        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
        if (isClassBodyProfile.profile(rootNode instanceof ClassBodyRootNode)) {
            assert PArguments.getSpecialArgument(arguments) == null : "there cannot be a special argument in a class body";
            PArguments.setSpecialArgument(arguments, rootNode);
        }
    }

    protected static boolean isBuiltin(Object callee) {
        return callee instanceof PBuiltinFunction || callee instanceof PBuiltinMethod;
    }

    @Override
    public Node copy() {
        AbstractInvokeNode copy = (AbstractInvokeNode) super.copy();
        copy.excSlot = null;
        copy.excSlotInitialized = false;
        return copy;
    }
}

public abstract class InvokeNode extends AbstractInvokeNode {
    private static final GenericInvokeNode UNCACHED = new GenericInvokeNode();
    @Child private DirectCallNode callNode;
    private final PythonObject globals;
    private final PCell[] closure;
    protected final boolean isBuiltin;

    protected InvokeNode(CallTarget callTarget, PythonObject globals, PCell[] closure, boolean isBuiltin, boolean isGenerator) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        if (isBuiltin && PythonOptions.getEnableForcedSplits()) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && shouldInlineGenerators()) {
            this.callNode.forceInlining();
        }
        this.globals = globals;
        this.closure = closure;
        this.isBuiltin = isBuiltin;
    }

    public abstract Object execute(VirtualFrame frame, Object[] arguments);

    @TruffleBoundary
    public static InvokeNode create(PFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return InvokeNodeGen.create(callTarget, callee.getGlobals(), callee.getClosure(), builtin, callee.isGeneratorFunction());
    }

    @TruffleBoundary
    public static InvokeNode create(PBuiltinFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return InvokeNodeGen.create(callTarget, null, null, builtin, false);
    }

    public static Object invokeUncached(PFunction callee, Object[] arguments) {
        return UNCACHED.execute(null, callee, arguments);
    }

    public static Object invokeUncached(PBuiltinFunction callee, Object[] arguments) {
        return UNCACHED.execute(null, callee, arguments);
    }

    @Specialization
    @SuppressWarnings("try")
    protected Object doNoKeywords(VirtualFrame frame, Object[] arguments) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        RootCallTarget ct = (RootCallTarget) callNode.getCallTarget();
        optionallySetClassBodySpecial(arguments, ct);
        CallContext.enter(frame, arguments, ct, this);
        return callNode.call(arguments);
    }

    public final RootNode getCurrentRootNode() {
        return callNode.getCurrentRootNode();
    }
}

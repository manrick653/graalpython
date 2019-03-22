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

package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.TruffleObject)
public class TruffleObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TruffleObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doForeignObject(Object self,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("createIfTrueNode()") CastToBooleanNode cast) {
            try {
                if (lib.isBoolean(self)) {
                    return lib.asBoolean(self);
                } else if (lib.fitsInLong(self)) {
                    return cast.executeWith(lib.asLong(self));
                } else if (lib.fitsInDouble(self)) {
                    return cast.executeWith(lib.asDouble(self));
                } else if (lib.hasArrayElements(self)) {
                    return cast.executeWith(lib.getArraySize(self));
                } else {
                    return !lib.isNull(self);
                }
            } catch (UnsupportedMessageException e) {
                throw raise(TypeError, "foreign.__bool__ should return a boolean value");
            }
        }
    }

    static Object[] unpackForeignArray(Object left, InteropLibrary lib, PForeignToPTypeNode convert) {
        try {
            long sizeObj = lib.getArraySize(left);
            if (sizeObj < Integer.MAX_VALUE) {
                int size = (int) sizeObj;
                Object[] data = new Object[size];

                // read data
                for (int i = 0; i < size; i++) {
                    data[i] = convert.executeConvert(lib.readArrayElement(left, i));
                }

                return data;
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            // fall through
        }
        return null;
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public long len(Object self,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {

            try {
                if (lib.hasArrayElements(self)) {
                    return lib.getArraySize(self);
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            throw raise(AttributeError, "'foreign' object has no attribute 'len'");
        }
    }

    abstract static class ForeignBinaryNode extends PythonBinaryBuiltinNode {
        @Child LookupAndCallBinaryNode op;
        protected final boolean reverse;

        protected ForeignBinaryNode(LookupAndCallBinaryNode op, boolean reverse) {
            this.op = op;
            this.reverse = reverse;
        }

        protected static boolean isNegativeNumber(InteropLibrary lib, Object right) {
            long val = 0;
            try {
                if (lib.fitsInByte(right)) {
                    val = lib.asByte(right);
                } else if (lib.fitsInShort(right)) {
                    val = lib.asShort(right);
                } else if (lib.fitsInInt(right)) {
                    val = lib.asInt(right);
                } else if (lib.fitsInLong(right)) {
                    val = lib.asLong(right);
                }
                return val < 0;
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return false;
        }

        protected static boolean isPythonLikeSequence(InteropLibrary lib, Object receiver) {
            return lib.hasArrayElements(receiver);
        }

        @Specialization(guards = {"!reverse", "lib.isBoolean(left)"})
        Object doComparisonBool(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(lib.asBoolean(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"!reverse", "lib.fitsInLong(left)"})
        Object doComparisonLong(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(lib.asLong(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"!reverse", "lib.fitsInDouble(left)"})
        Object doComparisonDouble(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(lib.asDouble(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"!reverse", "lib.isString(left)"})
        Object doComparisonString(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(lib.asString(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
        @Specialization(guards = {"reverse", "lib.isBoolean(right)"})
        Object doComparisonBoolR(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(left, lib.asBoolean(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"reverse", "lib.fitsInLong(right)"})
        Object doComparisonLongR(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(left, lib.asLong(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"reverse", "lib.fitsInDouble(right)"})
        Object doComparisonDoubleR(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(left, lib.asDouble(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"reverse", "lib.isString(right)"})
        Object doComparisonStringR(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return op.executeObject(left, lib.asString(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!reverse", "!lib.fitsInDouble(left)", "!lib.fitsInLong(left)", "!lib.isBoolean(left)"})
        public PNotImplemented doGeneric(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"reverse", "!lib.fitsInDouble(right)", "!lib.fitsInLong(right)", "!lib.isBoolean(right)"})
        public PNotImplemented doGenericReverse(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends ForeignBinaryNode {
        AddNode() {
            super(BinaryArithmetic.Add.create(), false);
        }

        AddNode(boolean reverse) {
            super(BinaryArithmetic.Add.create(), reverse);
        }

        @Specialization(insertBefore = "doGeneric", guards = {"lib.hasArrayElements(left)", "lib.hasArrayElements(right)"})
        static Object doForeignArray(Object left, Object right,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PForeignToPTypeNode convert) {
            Object[] unpackedLeft = unpackForeignArray(left, lib, convert);
            Object[] unpackedRight = unpackForeignArray(right, lib, convert);
            if (unpackedLeft != null && unpackedRight != null) {
                Object[] result = Arrays.copyOf(unpackedLeft, unpackedLeft.length + unpackedRight.length);
                for (int i = 0, j = unpackedLeft.length; i < unpackedRight.length && j < result.length; i++, j++) {
                    assert j < result.length;
                    result[j] = unpackedRight[i];
                }

                return factory.createList(result);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
        RAddNode() {
            super(true);
        }
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends ForeignBinaryNode {
        MulNode() {
            super(BinaryArithmetic.Mul.create(), false);
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"isPythonLikeSequence(lib, left)", "lib.fitsInInt(right)"})
        static Object doForeignArray(Object left, Object right,
                        @Cached PRaiseNode raise,
                        @Cached PythonObjectFactory factory,
                        @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                int rightInt = 0;
                try {
                    rightInt = lib.asInt(right);
                } catch (UnsupportedMessageException e) {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
                Object[] unpackForeignArray = unpackForeignArray(left, lib, convert);
                if (unpackForeignArray != null) {
                    Object[] repeatedData = new Object[Math.max(0, Math.multiplyExact(unpackForeignArray.length, rightInt))];

                    // repeat data
                    for (int i = 0; i < repeatedData.length; i++) {
                        repeatedData[i] = unpackForeignArray[i % rightInt];
                    }

                    return factory.createList(repeatedData);
                }
                return PNotImplemented.NOT_IMPLEMENTED;
            } catch (ArithmeticException e) {
                throw raise.raise(MemoryError);
            }
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"isPythonLikeSequence(lib, left)", "lib.isBoolean(right)"})
        static Object doForeignArrayForeignBoolean(Object left, Object right,
                        @Cached PRaiseNode raise,
                        @Cached PythonObjectFactory factory,
                        @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return doForeignArray(left, lib.asBoolean(right) ? 1 : 0, raise, factory, convert, lib);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(insertBefore = "doGeneric", guards = {"isPythonLikeSequence(lib, left)", "isNegativeNumber(lib, right)"})
        static Object doForeignArrayNegativeMult(Object left, Object right,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {

            return factory.createList();
        }

        @SuppressWarnings("unused")
        @Specialization(insertBefore = "doGeneric", guards = {"!lib.fitsInDouble(left)", "!lib.fitsInLong(left)", "!lib.isBoolean(left)", "!isPythonLikeSequence(lib, left)"})
        PNotImplemented doForeignGeneric(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends ForeignBinaryNode {
        RMulNode() {
            super(BinaryArithmetic.Mul.create(), true);
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"isPythonLikeSequence(lib, right)", "lib.fitsInInt(left)"})
        Object doForeignArray(Object right, Object left,
                        @Cached PRaiseNode raise,
                        @Cached PythonObjectFactory factory,
                        @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            return MulNode.doForeignArray(right, left, raise, factory, convert, lib);
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"isPythonLikeSequence(lib, right)", "lib.isBoolean(left)"})
        Object doForeignArrayForeignBoolean(Object right, Object left,
                        @Cached PRaiseNode raise,
                        @Cached PythonObjectFactory factory,
                        @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                return MulNode.doForeignArray(right, lib.asBoolean(left) ? 1 : 0, raise, factory, convert, lib);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(insertBefore = "doGeneric", guards = {"!lib.fitsInDouble(right)", "!lib.fitsInLong(right)", "!lib.isBoolean(right)", "!isPythonLikeSequence(lib, right)"})
        PNotImplemented doForeignGneeric(Object right, Object left,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends ForeignBinaryNode {
        SubNode() {
            super(BinaryArithmetic.Sub.create(), false);
        }
    }

    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RSubNode extends ForeignBinaryNode {
        RSubNode() {
            super(BinaryArithmetic.Sub.create(), true);
        }
    }

    @Builtin(name = __TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class TrueDivNode extends ForeignBinaryNode {
        TrueDivNode() {
            super(BinaryArithmetic.TrueDiv.create(), false);
        }
    }

    @Builtin(name = __RTRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RTrueDivNode extends ForeignBinaryNode {
        RTrueDivNode() {
            super(BinaryArithmetic.TrueDiv.create(), true);
        }
    }

    @Builtin(name = __FLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends ForeignBinaryNode {
        FloorDivNode() {
            super(BinaryArithmetic.FloorDiv.create(), false);
        }
    }

    @Builtin(name = __RFLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends ForeignBinaryNode {
        RFloorDivNode() {
            super(BinaryArithmetic.FloorDiv.create(), true);
        }
    }

    public abstract static class ForeignBinaryComparisonNode extends PythonBinaryBuiltinNode {
        @Child BinaryComparisonNode comparisonNode;

        protected ForeignBinaryComparisonNode(BinaryComparisonNode genericOp) {
            this.comparisonNode = genericOp;
        }

        @Specialization(guards = {"lib.isBoolean(left)"})
        Object doComparisonBool(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return comparisonNode.executeBool(lib.asBoolean(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"lib.fitsInLong(left)"})
        Object doComparisonLong(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return comparisonNode.executeWith(lib.asLong(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"lib.fitsInDouble(left)"})
        Object doComparisonDouble(Object left, Object right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return comparisonNode.executeWith(lib.asDouble(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = "lib.isNull(left)")
        Object doComparison(@SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return comparisonNode.executeWith(PNone.NONE, right);
        }

        @SuppressWarnings("unused")
        @Fallback
        public PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends ForeignBinaryComparisonNode {
        protected LtNode() {
            super(BinaryComparisonNode.create(__LT__, __GT__, "<"));
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends ForeignBinaryComparisonNode {
        protected LeNode() {
            super(BinaryComparisonNode.create(__LE__, __GE__, "<="));
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends ForeignBinaryComparisonNode {
        protected GtNode() {
            super(BinaryComparisonNode.create(__GT__, __LT__, ">"));
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends ForeignBinaryComparisonNode {
        protected GeNode() {
            super(BinaryComparisonNode.create(__GE__, __LE__, ">="));
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends ForeignBinaryComparisonNode {
        protected EqNode() {
            super(BinaryComparisonNode.create(__EQ__, __NE__, "=="));
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "lib.hasArrayElements(iterable)")
        Object doForeignArray(Object iterable,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                long size = lib.getArraySize(iterable);
                if (size < Integer.MAX_VALUE) {
                    return factory().createForeignArrayIterator(iterable, (int) size);
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            throw raise(TypeError, "'foreign' object is not iterable");
        }

        @Specialization(guards = "lib.isString(iterable)")
        Object doBoxedString(Object iterable,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return factory().createStringIterator(lib.asString(iterable));
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            throw raise(TypeError, "'foreign' object is not iterable");
        }

        @Fallback
        PNone doGeneric(@SuppressWarnings("unused") Object o) {
            throw raise(TypeError, "'foreign' object is not iterable");
        }
    }

    @Builtin(name = __NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonBuiltinNode {
        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.a
         */
        @Specialization(guards = {"isForeignObject(callee)", "!isNoValue(callee)", "keywords.length == 0"})
        protected Object doInteropCall(Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("create()") PTypeToForeignNode toForeignNode,
                        @Cached("create()") PForeignToPTypeNode toPTypeNode) {
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    convertedArgs[i] = toForeignNode.executeConvert(arguments[i]);
                }
                Object res = lib.instantiate(callee, convertedArgs);
                return toPTypeNode.executeConvert(res);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, "invalid instantiation of foreign object %s()", callee);
            }
        }

        @Fallback
        protected Object doGeneric(Object callee, @SuppressWarnings("unused") Object arguments, @SuppressWarnings("unused") Object keywords) {
            throw raise(PythonErrorType.TypeError, "invalid instantiation of foreign object %s()", callee);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        public final Object executeWithArgs(Object callee, Object[] arguments) {
            return this.execute(callee, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public abstract Object execute(Object callee, Object[] arguments, PKeyword[] keywords);

        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.
         */
        @Specialization(guards = {"isForeignObject(callee)", "!isNoValue(callee)", "keywords.length == 0"})
        protected Object doInteropCall(Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached("create()") PTypeToForeignNode toForeignNode,
                        @Cached("create()") PForeignToPTypeNode toPTypeNode) {
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    convertedArgs[i] = toForeignNode.executeConvert(arguments[i]);
                }
                if (lib.isExecutable(callee)) {
                    Object res = lib.execute(callee, convertedArgs);
                    return toPTypeNode.executeConvert(res);
                } else {
                    Object res = lib.instantiate(callee, convertedArgs);
                    return toPTypeNode.executeConvert(res);
                }
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, "invalid invocation of foreign callable %s()", callee);
            }
        }

        @Fallback
        protected Object doGeneric(Object callee, @SuppressWarnings("unused") Object arguments, @SuppressWarnings("unused") Object keywords) {
            throw raise(PythonErrorType.TypeError, "invalid invocation of foreign callable %s()", callee);
        }

        public static CallNode create() {
            return TruffleObjectBuiltinsFactory.CallNodeFactory.create(null);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Child AccessForeignItemNodes.GetForeignItemNode getForeignItemNode = AccessForeignItemNodes.GetForeignItemNode.create();

        @Specialization
        Object doit(Object object, Object key) {
            return getForeignItemNode.execute(object, key);
        }
    }

    @Builtin(name = __GETATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetattrNode extends PythonBinaryBuiltinNode {
        @Child PForeignToPTypeNode toPythonNode = PForeignToPTypeNode.create();

        @Specialization
        protected Object doIt(Object object, Object key,
                        @CachedLibrary(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)") InteropLibrary read) {
            try {
                String member = (String) key;
                if (read.isMemberReadable(object, member)) {
                    return toPythonNode.executeConvert(read.readMember(object, member));
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException ignore) {
            }
            throw raise(PythonErrorType.AttributeError, "foreign object %s has no attribute %s", object, key);
        }
    }

    @Builtin(name = __SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization
        protected PNone doIt(Object object, String key, Object value,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                lib.writeMember(object, key, value);
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw raise(PythonErrorType.AttributeError, "foreign object %s has no attribute %s", object, key);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetitemNode extends PythonTernaryBuiltinNode {
        @Child private AccessForeignItemNodes.SetForeignItemNode setForeignItemNode = AccessForeignItemNodes.SetForeignItemNode.create();

        @Specialization
        Object doit(Object object, Object key, Object value) {
            setForeignItemNode.execute(object, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone doIt(Object object, String key,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                lib.removeMember(object, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.AttributeError, "foreign object %s has no attribute %s", object, key);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelitemNode extends PythonBinaryBuiltinNode {
        AccessForeignItemNodes.RemoveForeignItemNode delForeignItemNode = AccessForeignItemNodes.RemoveForeignItemNode.create();

        @Specialization
        PNone doit(Object object, Object key) {
            delForeignItemNode.execute(object, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.hasMembers(object)) {
                try {
                    return lib.getMembers(object);
                } catch (UnsupportedMessageException e) {
                    throw raise(TypeError, "The object '%s' claims to have members, but does not return them", object);
                }
            } else {
                return factory().createList();
            }
        }
    }

    @Builtin(name = __INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (lib.fitsInInt(object)) {
                try {
                    return lib.asInt(object);
                } catch (UnsupportedMessageException e) {
                    throw raise(TypeError, "foreign value '%s' claims it fits into index-sized int, but doesn't", object);
                }
            }
            throw raiseIndexError();
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        protected final String method = __STR__;
        @Child private LookupAndCallUnaryNode callStrNode;
        @Child protected PythonUnaryBuiltinNode objectStrNode;

        @Specialization(guards = {"lib.isNull(object)"})
        protected Object doNull(@SuppressWarnings("unused") Object object,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            return getCallStrNode().executeObject(PNone.NONE);
        }

        @Specialization(guards = {"lib.isBoolean(object)"})
        protected Object doBool(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return getCallStrNode().executeObject(lib.asBoolean(object));
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("The object '%s' claims to be boxed, but does not support the appropriate unbox message");
            }
        }

        @Specialization(guards = {"lib.isString(object)"})
        protected Object doStr(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return getCallStrNode().executeObject(lib.asString(object));
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("The object '%s' claims to be boxed, but does not support the appropriate unbox message");
            }
        }

        @Specialization(guards = {"lib.fitsInLong(object)"})
        protected Object doLong(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return getCallStrNode().executeObject(lib.asLong(object));
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("The object '%s' claims to be boxed, but does not support the appropriate unbox message");
            }
        }

        @Specialization(guards = {"lib.fitsInDouble(object)"})
        protected Object doDouble(Object object,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                return getCallStrNode().executeObject(lib.asDouble(object));
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("The object '%s' claims to be boxed, but does not support the appropriate unbox message");
            }
        }

        @Specialization(guards = {"lib.hasArrayElements(object)"})
        protected Object doArray(Object object,
                        @Cached("create()") CastToListNode asList,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            try {
                long size = lib.getArraySize(object);
                if (size <= Integer.MAX_VALUE && size >= 0) {
                    PForeignArrayIterator iterable = factory().createForeignArrayIterator(object, (int) size);
                    return getCallStrNode().executeObject(asList.executeWith(iterable));
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return doIt(object);
        }

        @Fallback
        protected Object doIt(Object object) {
            return getObjectStrNode().execute(object);
        }

        private LookupAndCallUnaryNode getCallStrNode() {
            if (callStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callStrNode = insert(LookupAndCallUnaryNode.create(method));
            }
            return callStrNode;
        }

        protected PythonUnaryBuiltinNode getObjectStrNode() {
            if (objectStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectStrNode = insert(ObjectBuiltinsFactory.StrNodeFactory.create());
            }
            return objectStrNode;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
        protected final String method = __REPR__;

        @Override
        protected PythonUnaryBuiltinNode getObjectStrNode() {
            if (objectStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectStrNode = insert(ObjectBuiltinsFactory.ReprNodeFactory.create());
            }
            return objectStrNode;
        }
    }
}

package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * <strong>Summary of our implementation of traceback handling</strong>
 *
 * <p>
 * When a {@link com.oracle.graal.python.runtime.exception.PException} is thrown, Truffle collects
 * every frame that the exception passes through until its caught. Then, when asked for a traceback,
 * it provides a sequence of stack trace element objects that capture frames from the root node
 * where the exception was thrown up to the top level root node. This stack trace is created from
 * the frames captured during the unwinding and then the frames that are currently on stack, i.e. it
 * expects to be called in an exception handler, otherwise the stacktrace is incorrect - it would
 * contain frames from the place where you asked for the stacktrace not the place where the
 * exception occured. Additionally, the stacktrace is frozen on the first access and from then on,
 * Truffle always returns its cached copy.
 * </p>
 * <p>
 * Python, on the other hand, builds the traceback incrementally. Firstly, it only includes the
 * frames that the exception has passed through during the unwinding plus the frame where it was
 * caught. It doesn't include the frames above it (to the top). Secondly, the traceback is never
 * frozen. The traceback accumulates more frames when the exception gets reraised. To correct the
 * mismatch between Truffle and Python eception handling, we need to wrap {@link PException}s in
 * {@link LazyTraceback} objects when caught and adhere to particular rules of exception handling
 * mentioned below.
 * </p>
 *
 * <p>
 * {@link LazyTraceback} represents a (possibly empty) traceback segment. It consists of an optional
 * Python frame or frame reference to the frame where the exception was caught and a
 * {@link PException} which serves as a carrier of the Truffle stack trace. {@link LazyTraceback}
 * forms a linked list that gets prepended a new {@link LazyTraceback} each time the python
 * exception gets reraised, either explicitly (raise statement) or implicitly (for example, at the
 * end of finally). Each of these segments needs to have their own distinct {@link PException} to
 * avoid interference, therefore a caught {@link PException} must never be rethrown after being
 * added to the traceback and it must never be added to the traceback multiple times.
 * </p>
 *
 * <p>
 * The whole chain of {@link LazyTraceback} objects can be materialized into a linked list of
 * PTraceback objects. Due to all the parts of a segment being optional, it can also materialize to
 * nothing (null/None). The materialization is lazy and is split between {@link GetTracebackNode}
 * and accessor nodes in {@link TracebackBuiltins}. The purpose of {@link GetTracebackNode} is to do
 * the minimal amount of work necessary to determine whether the traceback will materialize to
 * something and is not empty. Then it either returns the {@link PTraceback} object or null.
 * </p>
 *
 * <p>
 * Rules for exception handling:
 * <ul>
 * <li>When you catch a {@link PException PException} and need to obtain its corresponding
 * {@link com.oracle.graal.python.builtins.objects.exception.PBaseException PBaseException}, use the
 * {@link PException#reifyAndGetPythonException(VirtualFrame) reifyAndGetPythonException} method,
 * unless you're just doing a simple class check. Try to avoid the
 * {@link PException#getExceptionObject() getExceptionObject} method unless you know what you're
 * doing.</li>
 * <li>{@link PException PException} must never be rethrown after it has been possibly exposed to
 * the program, because its Truffle stacktrace may already be frozen and it would not capture more
 * frames. If you need to rethrow without the catching site appearing in the traceback, use
 * {@link com.oracle.graal.python.builtins.objects.exception.PBaseException#getExceptionForReraise(LazyTraceback)
 * PBaseException.getExceptionForReraise} method to obtain a fresh {@link PException PException} to
 * throw</li>
 * </ul>
 * </p>
 */
@GenerateUncached
public abstract class GetTracebackNode extends Node {
    public abstract PTraceback execute(LazyTraceback tb);

    @Specialization(guards = "tb.isMaterialized()")
    PTraceback getMaterialized(LazyTraceback tb) {
        return tb.getTraceback();
    }

    // The common case for not yet materialized
    @Specialization(guards = {"!tb.isMaterialized()", "hasFrame(tb)"})
    PTraceback createTraceback(LazyTraceback tb,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        PTraceback newTraceback = factory.createTraceback(tb);
        tb.setTraceback(newTraceback);
        return newTraceback;
    }

    // LazyTracebacks without the frame occur on the C boundary and on the top level. When
    // the exception gets caught by python, the first traceback will always have the frame
    // from the exception handler, so this specialization should not occur outside the following
    // situations:
    // 1) On the top level
    // 2) When the traceback is requested by C (PyErr_Fetch etc.)
    // 3) When called back by the traceback's accessors (tb_frame/tb_next/tb_lineno)
    @TruffleBoundary
    @Specialization(guards = {"!tb.isMaterialized()", "!hasFrame(tb)"})
    PTraceback traverse(LazyTraceback tb,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        // The logic of skipping and cutting off frames here and in MaterializeTruffleStacktraceNode must be the same
        boolean skipFirst = tb.getException().shouldHideLocation();
        for (TruffleStackTraceElement element : tb.getException().getTruffleStackTrace()) {
            if (skipFirst) {
                skipFirst = false;
                continue;
            }
            if (tb.getException().shouldCutOffTraceback(element)) {
                break;
            }
            if (LazyTraceback.elementWantedForTraceback(element)) {
                return createTraceback(tb, factory);
            }
        }
        PTraceback newTraceback = null;
        if (tb.getNextChain() != null) {
            newTraceback = execute(tb.getNextChain());
        }
        tb.setTraceback(newTraceback);
        return newTraceback;
    }

    protected static boolean hasFrame(LazyTraceback tb) {
        return tb.getFrame() != null || tb.getFrameInfo() != null;
    }

    public static GetTracebackNode create() {
        return GetTracebackNodeGen.create();
    }
}

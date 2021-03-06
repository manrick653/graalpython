# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import _codecs


def decode(self, encoding="utf-8", errors="strict"):
    """Decode the bytes using the codec registered for encoding.

    encoding
      The encoding with which to decode the bytes.
    errors
      The error handling scheme to use for the handling of decoding errors.
      The default is 'strict' meaning that decoding errors raise a
      UnicodeDecodeError. Other possible values are 'ignore' and 'replace'
      as well as any other name registered with codecs.register_error that
      can handle UnicodeDecodeErrors.
    """
    return _codecs.decode(self, encoding=encoding, errors=errors)


bytes.decode = __graalpython__.builtin_method(decode)


def count(self, sub, start=None, end=None):
    arr = self
    if start and end:
        arr = self[start:end]
    elif start:
        arr = self[start:]
    elif end:
        arr = self[:end]

    matches = 0
    # TODO implement Boyer-Moore algorithm
    l_sub = len(sub)
    for i in range(len(arr)):
        matched = True
        for j in range(l_sub):
            if sub[j] != arr[i + j]:
                matched = False
                break
        if matched:
            matches += 1
    return matches


bytes.count = __graalpython__.builtin_method(count)


def rfind(self, sub, start=None, end=None):
    arr = self
    if start and end:
        arr = self[start:end]
    elif start:
        arr = self[start:]
    elif end:
        arr = self[:end]

    # TODO implement properly using a fast algorithm
    l_sub = len(sub)
    if l_sub == 0:
        return len(arr)

    for i in range(len(arr), 0, -1):
        matched = True
        j = 0
        while j < l_sub and sub[l_sub - j - 1] == arr[i - j - 1]:
            j += 1
        if j >= l_sub:
            return i - j
    return -1


bytes.rfind = rfind

def strip(self, what=None):
    return self.lstrip(what).rstrip(what)


bytes.strip = strip

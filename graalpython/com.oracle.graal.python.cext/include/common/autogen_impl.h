/* MIT License
 *  
 * Copyright (c) 2020, Oracle and/or its affiliates. 
 * Copyright (c) 2019 pyhandle
 *  
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *  
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *  
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/*
   DO NOT EDIT THIS FILE!

   This file is automatically generated by tools/autogen.py from tools/public_api.h.
   Run this to regenerate:
       make autogen

*/

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromLong)(HPyContext ctx, long value)
{
    return _py2h(PyLong_FromLong(value));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromLongLong)(HPyContext ctx, long long v)
{
    return _py2h(PyLong_FromLongLong(v));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Long_FromUnsignedLongLong)(HPyContext ctx, unsigned long long v)
{
    return _py2h(PyLong_FromUnsignedLongLong(v));
}

HPyAPI_STORAGE long _HPy_IMPL_NAME(Long_AsLong)(HPyContext ctx, HPy h)
{
    return PyLong_AsLong(_h2py(h));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Float_FromDouble)(HPyContext ctx, double v)
{
    return _py2h(PyFloat_FromDouble(v));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Number_Add)(HPyContext ctx, HPy h1, HPy h2)
{
    return _py2h(PyNumber_Add(_h2py(h1), _h2py(h2)));
}

HPyAPI_STORAGE void _HPy_IMPL_NAME(Err_SetString)(HPyContext ctx, HPy h_type, const char *message)
{
    return PyErr_SetString(_h2py(h_type), message);
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Object_IsTrue)(HPyContext ctx, HPy h)
{
    return PyObject_IsTrue(_h2py(h));
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Bytes_Check)(HPyContext ctx, HPy h)
{
    return PyBytes_Check(_h2py(h));
}

HPyAPI_STORAGE HPy_ssize_t _HPy_IMPL_NAME(Bytes_Size)(HPyContext ctx, HPy h)
{
    return PyBytes_Size(_h2py(h));
}

HPyAPI_STORAGE HPy_ssize_t _HPy_IMPL_NAME(Bytes_GET_SIZE)(HPyContext ctx, HPy h)
{
    return PyBytes_GET_SIZE(_h2py(h));
}

HPyAPI_STORAGE char *_HPy_IMPL_NAME(Bytes_AsString)(HPyContext ctx, HPy h)
{
    return PyBytes_AsString(_h2py(h));
}

HPyAPI_STORAGE char *_HPy_IMPL_NAME(Bytes_AS_STRING)(HPyContext ctx, HPy h)
{
    return PyBytes_AS_STRING(_h2py(h));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_FromString)(HPyContext ctx, const char *utf8)
{
    return _py2h(PyUnicode_FromString(utf8));
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Unicode_Check)(HPyContext ctx, HPy h)
{
    return PyUnicode_Check(_h2py(h));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_AsUTF8String)(HPyContext ctx, HPy h)
{
    return _py2h(PyUnicode_AsUTF8String(_h2py(h)));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Unicode_FromWideChar)(HPyContext ctx, const wchar_t *w, HPy_ssize_t size)
{
    return _py2h(PyUnicode_FromWideChar(w, size));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(List_New)(HPyContext ctx, HPy_ssize_t len)
{
    return _py2h(PyList_New(len));
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(List_Append)(HPyContext ctx, HPy h_list, HPy h_item)
{
    return PyList_Append(_h2py(h_list), _h2py(h_item));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Dict_New)(HPyContext ctx)
{
    return _py2h(PyDict_New());
}

HPyAPI_STORAGE int _HPy_IMPL_NAME(Dict_SetItem)(HPyContext ctx, HPy h_dict, HPy h_key, HPy h_val)
{
    return PyDict_SetItem(_h2py(h_dict), _h2py(h_key), _h2py(h_val));
}

HPyAPI_STORAGE HPy _HPy_IMPL_NAME(Dict_GetItem)(HPyContext ctx, HPy h_dict, HPy h_key)
{
    return _py2h(PyDict_GetItem(_h2py(h_dict), _h2py(h_key)));
}


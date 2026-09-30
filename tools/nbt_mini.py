"""Tiny NBT reader/writer (gzip, big-endian) for structure templates.

Tags are represented as Python values wrapped in typed classes so the writer knows what to emit:
  Byte, Short, Int, Long, Float, Double, String -> plain wrapper classes
  List -> TagList(type_id, [values])
  Compound -> dict (keys in insertion order)
  ByteArray/IntArray/LongArray -> TypedArray
"""
import gzip
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(13)


class Byte(int): tag = TAG_BYTE
class Short(int): tag = TAG_SHORT
class Int(int): tag = TAG_INT
class Long(int): tag = TAG_LONG
class Float(float): tag = TAG_FLOAT
class Double(float): tag = TAG_DOUBLE
class String(str): tag = TAG_STRING


class TagList(list):
    tag = TAG_LIST

    def __init__(self, type_id, values=()):
        super().__init__(values)
        self.type_id = type_id


class TypedArray(list):
    def __init__(self, tag, values=()):
        super().__init__(values)
        self.tag = tag


def tag_of(v):
    if isinstance(v, dict):
        return TAG_COMPOUND
    return v.tag


# ---------------------------------------------------------------- reading

class Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def take(self, fmt):
        v = struct.unpack_from(">" + fmt, self.b, self.i)
        self.i += struct.calcsize(">" + fmt)
        return v[0]

    def string(self):
        n = self.take("H")
        s = self.b[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return s

    def payload(self, t):
        if t == TAG_BYTE: return Byte(self.take("b"))
        if t == TAG_SHORT: return Short(self.take("h"))
        if t == TAG_INT: return Int(self.take("i"))
        if t == TAG_LONG: return Long(self.take("q"))
        if t == TAG_FLOAT: return Float(self.take("f"))
        if t == TAG_DOUBLE: return Double(self.take("d"))
        if t == TAG_STRING: return String(self.string())
        if t == TAG_BYTE_ARRAY:
            n = self.take("i"); out = TypedArray(t, struct.unpack_from(">%db" % n, self.b, self.i)); self.i += n; return out
        if t == TAG_INT_ARRAY:
            n = self.take("i"); out = TypedArray(t, struct.unpack_from(">%di" % n, self.b, self.i)); self.i += 4 * n; return out
        if t == TAG_LONG_ARRAY:
            n = self.take("i"); out = TypedArray(t, struct.unpack_from(">%dq" % n, self.b, self.i)); self.i += 8 * n; return out
        if t == TAG_LIST:
            et = self.take("b"); n = self.take("i")
            return TagList(et, [self.payload(et) for _ in range(n)])
        if t == TAG_COMPOUND:
            d = {}
            while True:
                ct = self.take("b")
                if ct == TAG_END:
                    return d
                name = self.string()
                d[name] = self.payload(ct)
        raise ValueError("tag %d" % t)


def load(path):
    data = gzip.open(path, "rb").read()
    r = Reader(data)
    t = r.take("b")
    name = r.string()
    return name, r.payload(t)


# ---------------------------------------------------------------- writing

class Writer:
    def __init__(self):
        self.parts = []

    def put(self, fmt, *v):
        self.parts.append(struct.pack(">" + fmt, *v))

    def string(self, s):
        e = s.encode("utf-8")
        self.put("H", len(e))
        self.parts.append(e)

    def payload(self, v):
        t = tag_of(v)
        if t == TAG_BYTE: self.put("b", int(v))
        elif t == TAG_SHORT: self.put("h", int(v))
        elif t == TAG_INT: self.put("i", int(v))
        elif t == TAG_LONG: self.put("q", int(v))
        elif t == TAG_FLOAT: self.put("f", float(v))
        elif t == TAG_DOUBLE: self.put("d", float(v))
        elif t == TAG_STRING: self.string(str(v))
        elif t == TAG_BYTE_ARRAY: self.put("i", len(v)); self.put("%db" % len(v), *v)
        elif t == TAG_INT_ARRAY: self.put("i", len(v)); self.put("%di" % len(v), *v)
        elif t == TAG_LONG_ARRAY: self.put("i", len(v)); self.put("%dq" % len(v), *v)
        elif t == TAG_LIST:
            self.put("b", v.type_id); self.put("i", len(v))
            for e in v:
                self.payload(e)
        elif t == TAG_COMPOUND:
            for k, e in v.items():
                self.put("b", tag_of(e)); self.string(k); self.payload(e)
            self.put("b", TAG_END)
        else:
            raise ValueError("tag %r" % t)


def save(path, root, name=""):
    w = Writer()
    w.put("b", TAG_COMPOUND)
    w.string(name)
    w.payload(root)
    with gzip.open(path, "wb") as f:
        f.write(b"".join(w.parts))


def dump(v, indent=0, max_list=6):
    pad = "  " * indent
    if isinstance(v, dict):
        for k, e in v.items():
            if isinstance(e, (dict, TagList)) and len(e):
                print("%s%s:" % (pad, k)); dump(e, indent + 1, max_list)
            else:
                print("%s%s: %r" % (pad, k, e))
    elif isinstance(v, TagList):
        for i, e in enumerate(v):
            if i >= max_list:
                print("%s... (%d more)" % (pad, len(v) - i)); break
            if isinstance(e, (dict, TagList)):
                print("%s-" % pad); dump(e, indent + 1, max_list)
            else:
                print("%s- %r" % (pad, e))

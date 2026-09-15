#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

"""Generate the preprocessed C input that cc1 compiles.

A compiler benchmark needs preprocessed C.  This generates it: a single
translation unit of plain C89 with no #include at all, which makes it already
preprocessed by construction and free of any dependency on the host's headers
(a modern glibc header would not survive a 1994 parser anyway).

The generated code is written to give the passes something to chew on rather
than to compute anything meaningful: deep expressions for the folder, loops
with induction variables for the loop optimiser and strength reduction,
switches for jump-table generation, struct and array access for the
addressing-mode selector, recursion and many locals for the register
allocator, and repeated subexpressions for CSE.

    python3 mkinput.py <functions> > input.i
"""
import sys


def emit_function(i):
    return '''
struct rec%(i)d { int tag; long value; struct rec%(i)d *next; char name[16]; };

static int table%(i)d[64];

static long fold%(i)d (long a, long b, long c)
{
  return ((a + 3) * (b - 5) + (c << 2) - (a * b) / ((c | 1) + 7)
          + ((a ^ b) & 0xff) * ((b ^ c) | 0x10) - (a %% ((b & 31) + 1)));
}

static int loop%(i)d (int *v, int n)
{
  int i, j, s = 0;
  for (i = 0; i < n; i++)
    {
      s += v[i] * (i + 1);
      for (j = i; j < n; j += 2)
        s ^= v[j] + (i * j);
      if (s > 1000000)
        s >>= 3;
    }
  return s;
}

static long walk%(i)d (struct rec%(i)d *r, int depth)
{
  long acc = 0;
  while (r != 0)
    {
      switch (r->tag %% 6)
        {
        case 0: acc += r->value; break;
        case 1: acc -= r->value * 2; break;
        case 2: acc ^= r->value; break;
        case 3: acc += fold%(i)d (r->value, acc, depth); break;
        case 4: acc = (acc << 1) | (r->value & 1); break;
        default: acc += r->name[r->tag %% 15]; break;
        }
      if (depth > 0 && r->next != 0)
        acc += walk%(i)d (r->next, depth - 1);
      r = r->next;
    }
  return acc;
}

int entry%(i)d (int n, struct rec%(i)d *r)
{
  int k, s = 0;
  for (k = 0; k < 64; k++)
    table%(i)d[k] = k * k - n;
  s += loop%(i)d (table%(i)d, n < 64 ? n : 64);
  s += (int) walk%(i)d (r, 3);
  s += (int) fold%(i)d (n, s, k);
  return s;
}
''' % {'i': i}


def main():
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 8
    # No comment banner: cc1 is the compiler proper and never sees comments on
    # a real gcc run -- cpp has already removed them -- so its lexer does not
    # handle them.  A "/*" in the input produces "parse error before `/'" and
    # then a cascade of bogus type errors from the failed recovery.
    out = ['#line 1 "input.i"\n']
    for i in range(count):
        out.append(emit_function(i))

    # One function that calls all of them, so nothing is dead.
    out.append('\nint drive (int n)\n{\n  int s = 0;\n')
    for i in range(count):
        out.append('  s += entry%d (n + %d, 0);\n' % (i, i))
    out.append('  return s;\n}\n')
    sys.stdout.write(''.join(out))


if __name__ == '__main__':
    main()

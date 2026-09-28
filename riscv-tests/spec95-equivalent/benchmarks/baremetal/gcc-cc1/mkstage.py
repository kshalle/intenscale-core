#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

"""Stage the gcc 2.5.8 sources that use pre-ANSI variadic definitions.

This is the one place where the gcc port cannot work from the vendored tree
unmodified.  Twelve functions across eight files are defined in the pre-ANSI
varargs form:

    rtx
    gen_rtx (va_alist)
         va_dcl
    {
      va_list p;
      enum rtx_code code;
      ...
      va_start (p);
      code = va_arg (p, enum rtx_code);

That form relies on the compiler implementing the `va_alist` magic parameter,
which no compiler has done for over twenty years, and it cannot be expressed
in C89 either: a function taking `...` needs at least one named parameter.
C23 does allow `...` alone, but C23 also deleted the old-style parameter
declarations that the rest of these 1994 sources are written in, so there is
no single dialect that accepts the file as it stands.

So each such definition is rewritten here, into a copy under gen/src/, as:

    rtx
    gen_rtx (enum rtx_code bm_va_arg1, ...)
    {
      va_list p;
      enum rtx_code code;
      ...
      va_start (p, bm_va_arg1);
      code = bm_va_arg1;

The type and the variable are not hard-coded per function: they are read out
of the function's own first `va_arg` call, so the rewrite cannot disagree with
the source.  Everything else in the file is untouched, every rewrite is
reported, and the vendored tree is never written to.

    python3 mkstage.py [upstream-dir] [out-dir]
"""
import os
import re
import sys

# Only these files are part of the build (cc1's own sources, plus genattrtab,
# which runs on the build machine).  Functions defined elsewhere -- gcc.c and
# g++.c have their own variadic error()/fatal(), protoize and mips-tfile are
# separate programs -- must not have their declarations touched: cc1's error()
# and fatal() are different functions, and rewriting their declarations to
# match a driver's variadic ones breaks the fixed-argument definitions cc1
# actually compiles.
BUILD_FILES = set("""
toplev.c version.c tree.c print-tree.c stor-layout.c fold-const.c
function.c stmt.c expr.c calls.c expmed.c explow.c optabs.c varasm.c
rtl.c print-rtl.c rtlanal.c emit-rtl.c real.c
dbxout.c sdbout.c dwarfout.c xcoffout.c
integrate.c jump.c cse.c loop.c unroll.c flow.c stupid.c combine.c
regclass.c local-alloc.c global.c reload.c reload1.c caller-save.c
reorg.c sched.c final.c recog.c reg-stack.c getpwd.c convert.c
c-parse.c c-lang.c c-lex.c c-pragma.c c-decl.c c-typeck.c c-convert.c
c-aux-info.c c-common.c c-iterate.c bc-emit.c bc-optab.c obstack.c
genattrtab.c
""".split())

DEF_RE = re.compile(r'^(?P<name>[A-Za-z_][A-Za-z_0-9]*) \(va_alist\)\n\s*va_dcl\n',
                    re.M)


def rewrite(text, path, report):
    out = []
    pos = 0
    for m in DEF_RE.finditer(text):
        name = m.group('name')

        # The body follows; find this function's va_start and the first
        # va_arg assignment after it.
        body = text[m.end():]
        start = re.search(r'\bva_start \((?P<ap>[A-Za-z_][A-Za-z_0-9]*)\);', body)
        if start is None:
            report.append((path, name, None, 'no va_start found -- left alone'))
            continue
        ap = start.group('ap')
        first = re.search(r'(?P<lhs>[A-Za-z_][A-Za-z_0-9]*) = va_arg \(%s, (?P<type>[^)]+)\);'
                          % re.escape(ap), body[start.end():])
        if first is None:
            report.append((path, name, None, 'first va_arg is not a plain assignment -- left alone'))
            continue
        ctype = first.group('type').strip()
        lhs = first.group('lhs')

        # Splice: definition line, then va_start, then the first va_arg.
        out.append(text[pos:m.start()])
        out.append('%s (%s bm_va_arg1, ...)\n' % (name, ctype))

        seg_start = m.end()
        seg = text[seg_start:]
        s0 = start.start()
        s1 = start.end()
        f0 = start.end() + first.start()
        f1 = start.end() + first.end()

        out.append(seg[:s0])
        out.append('va_start (%s, bm_va_arg1);' % ap)
        out.append(seg[s1:f0])
        out.append('%s = bm_va_arg1;' % lhs)
        pos = seg_start + f1
        report.append((path, name, ctype, 'rewritten (first argument %s)' % lhs))

    out.append(text[pos:])
    return ''.join(out)


DECL_TMPL = (r'^(?P<pre>\s*(?:extern|static)?[^=;()]*?\b%s)\s*\(\s*\)'
             r'(?P<post>\s*;[ \t]*(?:/\*.*\*/)?[ \t]*)$')


def fix_declarations(text, funcs):
    """Rewrite old-style "NAME ();" declarations to match the new definition.

    A pre-ANSI "()" declaration says nothing about the arguments, and C does
    not allow it to be completed by a variadic definition -- gcc rejects the
    pair outright.  gcc 2.5.8 declares some of these functions that way in its
    headers (tree.h's "extern tree build ();", expr.h's emit_library_call) and
    some inside the file itself, so every such declaration has to move with
    the definition.
    """
    changed = []
    for name, ctype in funcs.items():
        pat = re.compile(DECL_TMPL % re.escape(name), re.M)
        new, n = pat.subn(lambda m: '%s (%s, ...)%s' % (m.group('pre'), ctype, m.group('post')),
                          text)
        if n:
            text = new
            changed.append((name, n))
    return text, changed


# gcc 2.5.8's obstack.h uses the "cast as lvalue" GNU extension, which GCC
# removed in 4.0:  *((void **) __o->next_free)++ = datum.  These rewrites turn
# each one into a comma expression that assigns and then advances the pointer,
# preserving the macro's value semantics.  Each pattern is asserted to be
# present, so a future upstream change cannot silently skip one.
OBSTACK_FIXES = [
    ('(*((h)->next_free)++ = achar)',
     '(*((h)->next_free) = (achar), (h)->next_free++, (void) 0)'),
    ('*(__o->next_free)++ = 0;',
     '*(__o->next_free) = 0, __o->next_free++;'),
    ('*(__o->next_free)++ = (datum);',
     '*(__o->next_free) = (datum), __o->next_free++;'),
    ('*((void **)__o->next_free)++ = ((void *)datum);',
     '*((void **)__o->next_free) = ((void *)datum), __o->next_free += sizeof (void *);'),
    ('*((int *)__o->next_free)++ = ((int)datum);',
     '*((int *)__o->next_free) = ((int)datum), __o->next_free += sizeof (int);'),
    ('(*((void **)(h)->next_free)++ = (void *)aptr)',
     '(*((void **)(h)->next_free) = (void *)(aptr), (h)->next_free += sizeof (void *), (void) 0)'),
    ('(*((int *)(h)->next_free)++ = (int)aint)',
     '(*((int *)(h)->next_free) = (int)(aint), (h)->next_free += sizeof (int), (void) 0)'),
    ('*((h)->next_free)++ = 0)',
     '(*((h)->next_free) = 0, (h)->next_free++))'),
    ('*((h)->next_free)++ = (datum))',
     '(*((h)->next_free) = (datum), (h)->next_free++))'),
    ('(*((char **)(h)->next_free)++ = (char *)aptr)',
     '(*((char **)(h)->next_free) = (char *)(aptr), (h)->next_free += sizeof (char *), (void) 0)'),
]


def fix_obstack(text, report):
    for old, new in OBSTACK_FIXES:
        if old not in text:
            raise SystemExit('mkstage.py: obstack.h pattern not found: %s' % old)
        text = text.replace(old, new)
    report.append(('obstack.h', '(macros)', None,
                   '%d cast-as-lvalue macros rewritten' % len(OBSTACK_FIXES)))
    return text


def main():
    upstream = sys.argv[1] if len(sys.argv) > 1 else '../../compiler-gcc/gcc-2.5.8'
    outdir = sys.argv[2] if len(sys.argv) > 2 else 'gen/src'
    os.makedirs(outdir, exist_ok=True)

    report = []
    staged = []
    funcs = {}

    # Pass 1: rewrite the definitions, and record each function's first
    # argument type so the declarations can be matched to it.
    rewritten = {}
    for fn in sorted(os.listdir(upstream)):
        if not fn.endswith('.c'):
            continue
        path = os.path.join(upstream, fn)
        with open(path, errors='surrogateescape') as f:
            text = f.read()
        if '(va_alist)' not in text:
            continue
        before = list(report)
        new = rewrite(text, fn, report)
        for _, name, ctype, note in report[len(before):]:
            if ctype is not None and fn in BUILD_FILES:
                funcs[name] = ctype
        if fn in BUILD_FILES:
            rewritten[fn] = (text, new)

    # obstack.h: independent of the varargs work, but the same mechanism.
    with open(os.path.join(upstream, 'obstack.h'), errors='surrogateescape') as f:
        ob = f.read()
    with open(os.path.join(outdir, 'obstack.h'), 'w', errors='surrogateescape') as f:
        f.write('/* Staged copy of obstack.h: cast-as-lvalue macros rewritten.\n'
                '   Generated by mkstage.py -- do not edit. */\n' + fix_obstack(ob, report))
    staged.append('obstack.h')

    # Pass 2: every file, source or header, that declares one of them.
    for fn in sorted(os.listdir(upstream)):
        if not (fn.endswith('.c') or fn.endswith('.h')):
            continue
        path = os.path.join(upstream, fn)
        if fn in rewritten:
            text, new = rewritten[fn]
        else:
            with open(path, errors='surrogateescape') as f:
                text = f.read()
            new = text
        new, changed = fix_declarations(new, funcs)
        for name, n in changed:
            report.append((fn, name, funcs[name],
                           'declaration updated (%d occurrence%s)' % (n, '' if n == 1 else 's')))
        needs_stdio = ('FILE' in ''.join(c[0] for c in changed) or
                       any('FILE' in funcs.get(name, '') for name, _ in changed))
        if needs_stdio and fn.endswith('.h') and '<stdio.h>' not in new:
            # The rewritten declaration names FILE, and gcc 2.5.8's headers do
            # not include stdio.h themselves -- callers included it first.
            new = '#include <stdio.h>\n' + new
            report.append((fn, '(header)', None, 'added #include <stdio.h> for FILE'))
        if new != text:
            with open(os.path.join(outdir, fn), 'w', errors='surrogateescape') as f:
                f.write('/* Staged copy of %s: pre-ANSI variadic definitions and the\n'
                        '   old-style declarations that go with them, rewritten for a current\n'
                        '   compiler.  Generated by mkstage.py -- do not edit.\n'
                        '   See mkstage.py for why this is necessary and what changed. */\n'
                        % fn + new)
            staged.append(fn)

    print('staged %d files into %s:' % (len(staged), outdir))
    for path, name, ctype, note in report:
        print('  %-16s %-24s %s' % (path, name, note))


if __name__ == '__main__':
    main()

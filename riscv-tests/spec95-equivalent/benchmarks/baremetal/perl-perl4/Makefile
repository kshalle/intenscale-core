# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Perl interpreter workload, bare metal: perl 4.036 on the RISC-V target.
#
# The upstream tree in ../../interpreter-perl is compiled unmodified.  What
# this port adds:
#   config.h        our copy of the Configure-generated header, with the
#                   facilities a bare-metal target lacks switched off.  It is
#                   found ahead of the upstream copy via -I. so the vendored
#                   tree stays pristine.
#   bm_os_stubs.c   the process/user/directory calls perl reaches for anyway
#   workload/       the benchmark scripts, written for this suite
#
#   make                   build
#   make run               the primes workload on spike
#   make run-anagram       the anagram workload
#   make BM_ENV=linux run  either one, as a static Linux binary under qemu

.DEFAULT_GOAL := all

BM_NAME  = perl-perl4
UPSTREAM = ../../interpreter-perl/perl-4.036

# perly.c is not shipped -- perl 4 generates its parser at build time.  This
# runs on the host, like gcc's generator programs.
YACC ?= bison -y

UPSTREAM_C = array cmd cons consarg doarg doio dolist dump eval form hash \
             perl regcomp regexec stab str toke util usersub

# malloc.c is deliberately absent: config.h turns MYMALLOC off and uses
# newlib's allocator, which sits on the harness _sbrk.
BM_SRCS = $(addprefix $(UPSTREAM)/,$(addsuffix .c,$(UPSTREAM_C))) \
          perly.c bm_os_stubs.c

# Our config.h is force-included ahead of everything.  -I. is not enough:
# perl.h says #include "config.h", and a quoted include searches the including
# file's own directory first, which would always find the upstream copy.
# Force-including ours first works because both files carry the same
# "#ifndef config_h" guard, so the upstream one then expands to nothing.
# -I- is the load-bearing flag here.  perl 4 generates its parser at build
# time and expects the regenerated perly.h to replace the copy that ships in
# the source directory: the shipped one numbers its first token 257 (AT&T
# yacc), bison numbers it 258, and the lexer and the parser must agree or
# every statement is a syntax error.  A quoted #include normally searches the
# including file's own directory first, so toke.c would always find the stale
# upstream perly.h.  -I- turns that off, so the ordering below decides:
# our generated perly.h and our config.h win, and the upstream directory
# still serves everything else.
# config.h leaves I_TIME off and I_SYS_TIME on, which is right for newlib (and
# was right for the 1991 systems perl 4 was configured against): <sys/time.h>
# declared struct tm as well.  glibc separates them, so the Linux build has to
# ask for <time.h> too, or the localtime/gmtime builtins do not compile.
BM_CFLAGS_LINUX = -DI_TIME

BM_CFLAGS = -include config.h -I. -I- -I$(UPSTREAM) -w

include ../common/bm.mk

perly.c perly.h: $(UPSTREAM)/perly.y
	$(YACC) -d -o perly.c $<
	@echo 'extern YYSTYPE yylval;' >> perly.h
# bison's header defines YYDEBUG (as 0); perl's toke.c tests it with #ifdef,
# not #if, and the code it then compiles needs perl's own -DDEBUGGING build.
# Undefining it at the end of the header keeps both sides happy: the parser's
# own "#if YYDEBUG" blocks still evaluate false.
	@echo '#undef YYDEBUG' >> perly.h

# perly.fixer is not used: it patches AT&T/BSD yacc output from 1991 for
# stack-growth bugs that bison does not have.

$(BM_OUT): perly.c perly.h

# Problem size lives in the workload arguments, not the binary, so switching
# size needs no rebuild.
#   tiny  ~ bring-up      small ~ a routine RTL run      ref ~ spike/host
SIZE ?= small
# Instruction counts measured on spike, primes / anagram:
#   tiny    22 M / 11 M      small   89 M / 76 M      ref  709 M / ~300 M
ifeq ($(SIZE),tiny)
  PRIMES_ARGS = 400 100
  WORDS = 400
  REPS  = 1
else ifeq ($(SIZE),small)
  PRIMES_ARGS = 2000 400
  WORDS = 2000
  REPS  = 2
else ifeq ($(SIZE),ref)
  PRIMES_ARGS = 20000 3000
  WORDS = 8000
  REPS  = 4
else
  $(error SIZE must be one of tiny, small, ref)
endif

# The word list is generated, not vendored: a real dictionary is someone
# else's copyrighted content.  See workload/mkwords.awk.
workload/words-$(WORDS).txt: workload/mkwords.awk
	awk -v n=$(WORDS) -f $< > $@

WORDLIST = workload/words-$(WORDS).txt

all: $(BM_OUT) $(WORDLIST)

# This port has two workloads.  `make run` drives primes; run-anagram drives
# the other.  Both work in either environment -- BM_RUN is spike or qemu
# according to BM_ENV.
BM_RUN_ARGS = workload/primes.pl $(PRIMES_ARGS)

.PHONY: run-primes run-anagram
run-primes: run

run-anagram: all
	$(BM_RUN) workload/anagram.pl $(WORDLIST) $(REPS)

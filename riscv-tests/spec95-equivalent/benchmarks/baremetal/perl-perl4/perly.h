/* A Bison parser, made by GNU Bison 3.0.4.  */

/* Bison interface for Yacc-like parsers in C

   Copyright (C) 1984, 1989-1990, 2000-2015 Free Software Foundation, Inc.

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.  */

/* As a special exception, you may create a larger work that contains
   part or all of the Bison parser skeleton and distribute that work
   under terms of your choice, so long as that work isn't itself a
   parser generator using the skeleton or a modified version thereof
   as a parser skeleton.  Alternatively, if you modify or redistribute
   the parser skeleton itself, you may (at your option) remove this
   special exception, which will cause the skeleton and the resulting
   Bison output files to be licensed under the GNU General Public
   License without this special exception.

   This special exception was added by the Free Software Foundation in
   version 2.2 of Bison.  */

#ifndef YY_YY_PERLY_H_INCLUDED
# define YY_YY_PERLY_H_INCLUDED
/* Debug traces.  */
#ifndef YYDEBUG
# define YYDEBUG 0
#endif
#if YYDEBUG
extern int yydebug;
#endif

/* Token type.  */
#ifndef YYTOKENTYPE
# define YYTOKENTYPE
  enum yytokentype
  {
    WORD = 258,
    LABEL = 259,
    APPEND = 260,
    OPEN = 261,
    SSELECT = 262,
    LOOPEX = 263,
    DOTDOT = 264,
    USING = 265,
    FORMAT = 266,
    DO = 267,
    SHIFT = 268,
    PUSH = 269,
    POP = 270,
    LVALFUN = 271,
    WHILE = 272,
    UNTIL = 273,
    IF = 274,
    UNLESS = 275,
    ELSE = 276,
    ELSIF = 277,
    CONTINUE = 278,
    SPLIT = 279,
    FLIST = 280,
    FOR = 281,
    FILOP = 282,
    FILOP2 = 283,
    FILOP3 = 284,
    FILOP4 = 285,
    FILOP22 = 286,
    FILOP25 = 287,
    FUNC0 = 288,
    FUNC1 = 289,
    FUNC2 = 290,
    FUNC2x = 291,
    FUNC3 = 292,
    FUNC4 = 293,
    FUNC5 = 294,
    HSHFUN = 295,
    HSHFUN3 = 296,
    FLIST2 = 297,
    SUB = 298,
    FILETEST = 299,
    LOCAL = 300,
    DELETE = 301,
    RELOP = 302,
    EQOP = 303,
    MULOP = 304,
    ADDOP = 305,
    PACKAGE = 306,
    AMPER = 307,
    FORMLIST = 308,
    REG = 309,
    ARYLEN = 310,
    ARY = 311,
    HSH = 312,
    STAR = 313,
    SUBST = 314,
    PATTERN = 315,
    RSTRING = 316,
    TRANS = 317,
    LISTOP = 318,
    OROR = 319,
    ANDAND = 320,
    UNIOP = 321,
    LS = 322,
    RS = 323,
    MATCH = 324,
    NMATCH = 325,
    UMINUS = 326,
    POW = 327,
    INC = 328,
    DEC = 329
  };
#endif
/* Tokens.  */
#define WORD 258
#define LABEL 259
#define APPEND 260
#define OPEN 261
#define SSELECT 262
#define LOOPEX 263
#define DOTDOT 264
#define USING 265
#define FORMAT 266
#define DO 267
#define SHIFT 268
#define PUSH 269
#define POP 270
#define LVALFUN 271
#define WHILE 272
#define UNTIL 273
#define IF 274
#define UNLESS 275
#define ELSE 276
#define ELSIF 277
#define CONTINUE 278
#define SPLIT 279
#define FLIST 280
#define FOR 281
#define FILOP 282
#define FILOP2 283
#define FILOP3 284
#define FILOP4 285
#define FILOP22 286
#define FILOP25 287
#define FUNC0 288
#define FUNC1 289
#define FUNC2 290
#define FUNC2x 291
#define FUNC3 292
#define FUNC4 293
#define FUNC5 294
#define HSHFUN 295
#define HSHFUN3 296
#define FLIST2 297
#define SUB 298
#define FILETEST 299
#define LOCAL 300
#define DELETE 301
#define RELOP 302
#define EQOP 303
#define MULOP 304
#define ADDOP 305
#define PACKAGE 306
#define AMPER 307
#define FORMLIST 308
#define REG 309
#define ARYLEN 310
#define ARY 311
#define HSH 312
#define STAR 313
#define SUBST 314
#define PATTERN 315
#define RSTRING 316
#define TRANS 317
#define LISTOP 318
#define OROR 319
#define ANDAND 320
#define UNIOP 321
#define LS 322
#define RS 323
#define MATCH 324
#define NMATCH 325
#define UMINUS 326
#define POW 327
#define INC 328
#define DEC 329

/* Value type.  */
#if ! defined YYSTYPE && ! defined YYSTYPE_IS_DECLARED

union YYSTYPE
{
#line 55 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1909  */

    int	ival;
    char *cval;
    ARG *arg;
    CMD *cmdval;
    struct compcmd compval;
    STAB *stabval;
    FCMD *formval;

#line 212 "perly.h" /* yacc.c:1909  */
};

typedef union YYSTYPE YYSTYPE;
# define YYSTYPE_IS_TRIVIAL 1
# define YYSTYPE_IS_DECLARED 1
#endif


extern YYSTYPE yylval;

int yyparse (void);

#endif /* !YY_YY_PERLY_H_INCLUDED  */
extern YYSTYPE yylval;
#undef YYDEBUG

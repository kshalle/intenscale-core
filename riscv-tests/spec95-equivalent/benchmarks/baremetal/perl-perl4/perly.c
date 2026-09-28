/* A Bison parser, made by GNU Bison 3.0.4.  */

/* Bison implementation for Yacc-like parsers in C

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

/* C LALR(1) parser skeleton written by Richard Stallman, by
   simplifying the original so-called "semantic" parser.  */

/* All symbols defined below should begin with yy or YY, to avoid
   infringing on user name space.  This should be done even for local
   variables, as they might otherwise be expanded by user macros.
   There are some unavoidable exceptions within include files to
   define necessary library symbols; they are noted "INFRINGES ON
   USER NAME SPACE" below.  */

/* Identify Bison output.  */
#define YYBISON 1

/* Bison version.  */
#define YYBISON_VERSION "3.0.4"

/* Skeleton name.  */
#define YYSKELETON_NAME "yacc.c"

/* Pure parsers.  */
#define YYPURE 0

/* Push parsers.  */
#define YYPUSH 0

/* Pull parsers.  */
#define YYPULL 1




/* Copy the first part of user declarations.  */
#line 39 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:339  */

#include "INTERN.h"
#include "perl.h"

/*SUPPRESS 530*/
/*SUPPRESS 593*/
/*SUPPRESS 595*/

STAB *scrstab;
ARG *arg4;	/* rarely used arguments to make_op() */
ARG *arg5;


#line 80 "perly.c" /* yacc.c:339  */

# ifndef YY_NULLPTR
#  if defined __cplusplus && 201103L <= __cplusplus
#   define YY_NULLPTR nullptr
#  else
#   define YY_NULLPTR 0
#  endif
# endif

/* Enabling verbose error messages.  */
#ifdef YYERROR_VERBOSE
# undef YYERROR_VERBOSE
# define YYERROR_VERBOSE 1
#else
# define YYERROR_VERBOSE 0
#endif

/* In a future release of Bison, this section will be replaced
   by #include "perly.h".  */
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
#line 55 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:355  */

    int	ival;
    char *cval;
    ARG *arg;
    CMD *cmdval;
    struct compcmd compval;
    STAB *stabval;
    FCMD *formval;

#line 278 "perly.c" /* yacc.c:355  */
};

typedef union YYSTYPE YYSTYPE;
# define YYSTYPE_IS_TRIVIAL 1
# define YYSTYPE_IS_DECLARED 1
#endif


extern YYSTYPE yylval;

int yyparse (void);

#endif /* !YY_YY_PERLY_H_INCLUDED  */

/* Copy the second part of user declarations.  */

#line 295 "perly.c" /* yacc.c:358  */

#ifdef short
# undef short
#endif

#ifdef YYTYPE_UINT8
typedef YYTYPE_UINT8 yytype_uint8;
#else
typedef unsigned char yytype_uint8;
#endif

#ifdef YYTYPE_INT8
typedef YYTYPE_INT8 yytype_int8;
#else
typedef signed char yytype_int8;
#endif

#ifdef YYTYPE_UINT16
typedef YYTYPE_UINT16 yytype_uint16;
#else
typedef unsigned short int yytype_uint16;
#endif

#ifdef YYTYPE_INT16
typedef YYTYPE_INT16 yytype_int16;
#else
typedef short int yytype_int16;
#endif

#ifndef YYSIZE_T
# ifdef __SIZE_TYPE__
#  define YYSIZE_T __SIZE_TYPE__
# elif defined size_t
#  define YYSIZE_T size_t
# elif ! defined YYSIZE_T
#  include <stddef.h> /* INFRINGES ON USER NAME SPACE */
#  define YYSIZE_T size_t
# else
#  define YYSIZE_T unsigned int
# endif
#endif

#define YYSIZE_MAXIMUM ((YYSIZE_T) -1)

#ifndef YY_
# if defined YYENABLE_NLS && YYENABLE_NLS
#  if ENABLE_NLS
#   include <libintl.h> /* INFRINGES ON USER NAME SPACE */
#   define YY_(Msgid) dgettext ("bison-runtime", Msgid)
#  endif
# endif
# ifndef YY_
#  define YY_(Msgid) Msgid
# endif
#endif

#ifndef YY_ATTRIBUTE
# if (defined __GNUC__                                               \
      && (2 < __GNUC__ || (__GNUC__ == 2 && 96 <= __GNUC_MINOR__)))  \
     || defined __SUNPRO_C && 0x5110 <= __SUNPRO_C
#  define YY_ATTRIBUTE(Spec) __attribute__(Spec)
# else
#  define YY_ATTRIBUTE(Spec) /* empty */
# endif
#endif

#ifndef YY_ATTRIBUTE_PURE
# define YY_ATTRIBUTE_PURE   YY_ATTRIBUTE ((__pure__))
#endif

#ifndef YY_ATTRIBUTE_UNUSED
# define YY_ATTRIBUTE_UNUSED YY_ATTRIBUTE ((__unused__))
#endif

#if !defined _Noreturn \
     && (!defined __STDC_VERSION__ || __STDC_VERSION__ < 201112)
# if defined _MSC_VER && 1200 <= _MSC_VER
#  define _Noreturn __declspec (noreturn)
# else
#  define _Noreturn YY_ATTRIBUTE ((__noreturn__))
# endif
#endif

/* Suppress unused-variable warnings by "using" E.  */
#if ! defined lint || defined __GNUC__
# define YYUSE(E) ((void) (E))
#else
# define YYUSE(E) /* empty */
#endif

#if defined __GNUC__ && 407 <= __GNUC__ * 100 + __GNUC_MINOR__
/* Suppress an incorrect diagnostic about yylval being uninitialized.  */
# define YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN \
    _Pragma ("GCC diagnostic push") \
    _Pragma ("GCC diagnostic ignored \"-Wuninitialized\"")\
    _Pragma ("GCC diagnostic ignored \"-Wmaybe-uninitialized\"")
# define YY_IGNORE_MAYBE_UNINITIALIZED_END \
    _Pragma ("GCC diagnostic pop")
#else
# define YY_INITIAL_VALUE(Value) Value
#endif
#ifndef YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
# define YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
# define YY_IGNORE_MAYBE_UNINITIALIZED_END
#endif
#ifndef YY_INITIAL_VALUE
# define YY_INITIAL_VALUE(Value) /* Nothing. */
#endif


#if ! defined yyoverflow || YYERROR_VERBOSE

/* The parser invokes alloca or malloc; define the necessary symbols.  */

# ifdef YYSTACK_USE_ALLOCA
#  if YYSTACK_USE_ALLOCA
#   ifdef __GNUC__
#    define YYSTACK_ALLOC __builtin_alloca
#   elif defined __BUILTIN_VA_ARG_INCR
#    include <alloca.h> /* INFRINGES ON USER NAME SPACE */
#   elif defined _AIX
#    define YYSTACK_ALLOC __alloca
#   elif defined _MSC_VER
#    include <malloc.h> /* INFRINGES ON USER NAME SPACE */
#    define alloca _alloca
#   else
#    define YYSTACK_ALLOC alloca
#    if ! defined _ALLOCA_H && ! defined EXIT_SUCCESS
#     include <stdlib.h> /* INFRINGES ON USER NAME SPACE */
      /* Use EXIT_SUCCESS as a witness for stdlib.h.  */
#     ifndef EXIT_SUCCESS
#      define EXIT_SUCCESS 0
#     endif
#    endif
#   endif
#  endif
# endif

# ifdef YYSTACK_ALLOC
   /* Pacify GCC's 'empty if-body' warning.  */
#  define YYSTACK_FREE(Ptr) do { /* empty */; } while (0)
#  ifndef YYSTACK_ALLOC_MAXIMUM
    /* The OS might guarantee only one guard page at the bottom of the stack,
       and a page size can be as small as 4096 bytes.  So we cannot safely
       invoke alloca (N) if N exceeds 4096.  Use a slightly smaller number
       to allow for a few compiler-allocated temporary stack slots.  */
#   define YYSTACK_ALLOC_MAXIMUM 4032 /* reasonable circa 2006 */
#  endif
# else
#  define YYSTACK_ALLOC YYMALLOC
#  define YYSTACK_FREE YYFREE
#  ifndef YYSTACK_ALLOC_MAXIMUM
#   define YYSTACK_ALLOC_MAXIMUM YYSIZE_MAXIMUM
#  endif
#  if (defined __cplusplus && ! defined EXIT_SUCCESS \
       && ! ((defined YYMALLOC || defined malloc) \
             && (defined YYFREE || defined free)))
#   include <stdlib.h> /* INFRINGES ON USER NAME SPACE */
#   ifndef EXIT_SUCCESS
#    define EXIT_SUCCESS 0
#   endif
#  endif
#  ifndef YYMALLOC
#   define YYMALLOC malloc
#   if ! defined malloc && ! defined EXIT_SUCCESS
void *malloc (YYSIZE_T); /* INFRINGES ON USER NAME SPACE */
#   endif
#  endif
#  ifndef YYFREE
#   define YYFREE free
#   if ! defined free && ! defined EXIT_SUCCESS
void free (void *); /* INFRINGES ON USER NAME SPACE */
#   endif
#  endif
# endif
#endif /* ! defined yyoverflow || YYERROR_VERBOSE */


#if (! defined yyoverflow \
     && (! defined __cplusplus \
         || (defined YYSTYPE_IS_TRIVIAL && YYSTYPE_IS_TRIVIAL)))

/* A type that is properly aligned for any stack member.  */
union yyalloc
{
  yytype_int16 yyss_alloc;
  YYSTYPE yyvs_alloc;
};

/* The size of the maximum gap between one aligned stack and the next.  */
# define YYSTACK_GAP_MAXIMUM (sizeof (union yyalloc) - 1)

/* The size of an array large to enough to hold all stacks, each with
   N elements.  */
# define YYSTACK_BYTES(N) \
     ((N) * (sizeof (yytype_int16) + sizeof (YYSTYPE)) \
      + YYSTACK_GAP_MAXIMUM)

# define YYCOPY_NEEDED 1

/* Relocate STACK from its old location to the new one.  The
   local variables YYSIZE and YYSTACKSIZE give the old and new number of
   elements in the stack, and YYPTR gives the new location of the
   stack.  Advance YYPTR to a properly aligned location for the next
   stack.  */
# define YYSTACK_RELOCATE(Stack_alloc, Stack)                           \
    do                                                                  \
      {                                                                 \
        YYSIZE_T yynewbytes;                                            \
        YYCOPY (&yyptr->Stack_alloc, Stack, yysize);                    \
        Stack = &yyptr->Stack_alloc;                                    \
        yynewbytes = yystacksize * sizeof (*Stack) + YYSTACK_GAP_MAXIMUM; \
        yyptr += yynewbytes / sizeof (*yyptr);                          \
      }                                                                 \
    while (0)

#endif

#if defined YYCOPY_NEEDED && YYCOPY_NEEDED
/* Copy COUNT objects from SRC to DST.  The source and destination do
   not overlap.  */
# ifndef YYCOPY
#  if defined __GNUC__ && 1 < __GNUC__
#   define YYCOPY(Dst, Src, Count) \
      __builtin_memcpy (Dst, Src, (Count) * sizeof (*(Src)))
#  else
#   define YYCOPY(Dst, Src, Count)              \
      do                                        \
        {                                       \
          YYSIZE_T yyi;                         \
          for (yyi = 0; yyi < (Count); yyi++)   \
            (Dst)[yyi] = (Src)[yyi];            \
        }                                       \
      while (0)
#  endif
# endif
#endif /* !YYCOPY_NEEDED */

/* YYFINAL -- State number of the termination state.  */
#define YYFINAL  3
/* YYLAST -- Last index in YYTABLE.  */
#define YYLAST   2922

/* YYNTOKENS -- Number of terminals.  */
#define YYNTOKENS  93
/* YYNNTS -- Number of nonterminals.  */
#define YYNNTS  30
/* YYNRULES -- Number of rules.  */
#define YYNRULES  187
/* YYNSTATES -- Number of states.  */
#define YYNSTATES  423

/* YYTRANSLATE[YYX] -- Symbol number corresponding to YYX as returned
   by yylex, with out-of-bounds checking.  */
#define YYUNDEFTOK  2
#define YYMAXUTOK   329

#define YYTRANSLATE(YYX)                                                \
  ((unsigned int) (YYX) <= YYMAXUTOK ? yytranslate[YYX] : YYUNDEFTOK)

/* YYTRANSLATE[TOKEN-NUM] -- Symbol number corresponding to TOKEN-NUM
   as returned by yylex, without out-of-bounds checking.  */
static const yytype_uint8 yytranslate[] =
{
       0,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,    80,     2,     2,     2,     2,    74,     2,
      86,     4,     2,    90,    66,    89,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,    69,    88,
       2,    67,     2,    68,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,    91,     2,    92,    73,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     3,    72,    87,    81,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
       2,     2,     2,     2,     2,     2,     1,     2,     5,     6,
       7,     8,     9,    10,    11,    12,    13,    14,    15,    16,
      17,    18,    19,    20,    21,    22,    23,    24,    25,    26,
      27,    28,    29,    30,    31,    32,    33,    34,    35,    36,
      37,    38,    39,    40,    41,    42,    43,    44,    45,    46,
      47,    48,    49,    50,    51,    52,    53,    54,    55,    56,
      57,    58,    59,    60,    61,    62,    63,    64,    65,    70,
      71,    75,    76,    77,    78,    79,    82,    83,    84,    85
};

#if YYDEBUG
  /* YYRLINE[YYN] -- Source line where rule number YYN was defined.  */
static const yytype_uint16 yyrline[] =
{
       0,   112,   112,   112,   125,   127,   132,   133,   135,   140,
     150,   154,   155,   159,   161,   163,   164,   174,   179,   181,
     183,   186,   189,   192,   197,   200,   203,   206,   211,   215,
     219,   223,   227,   266,   293,   300,   305,   306,   310,   311,
     315,   316,   319,   321,   323,   327,   335,   339,   346,   367,
     371,   373,   376,   380,   387,   389,   391,   393,   395,   397,
     399,   401,   405,   407,   416,   418,   420,   422,   424,   426,
     428,   430,   432,   436,   438,   440,   442,   444,   446,   450,
     452,   454,   456,   458,   461,   464,   467,   470,   477,   480,
     486,   490,   492,   494,   497,   499,   501,   503,   506,   510,
     514,   520,   525,   530,   535,   541,   547,   553,   555,   557,
     559,   561,   563,   569,   574,   581,   588,   595,   601,   606,
     612,   617,   622,   624,   627,   629,   631,   633,   635,   641,
     643,   646,   653,   660,   664,   668,   673,   677,   681,   684,
     686,   688,   690,   692,   695,   700,   702,   704,   706,   708,
     713,   723,   726,   729,   733,   738,   743,   746,   750,   752,
     754,   756,   758,   762,   766,   770,   772,   775,   778,   783,
     788,   790,   791,   794,   799,   804,   811,   816,   821,   828,
     831,   834,   837,   841,   844,   848,   850,   859
};
#endif

#if YYDEBUG || YYERROR_VERBOSE || 0
/* YYTNAME[SYMBOL-NUM] -- String name of the symbol SYMBOL-NUM.
   First, the terminals, then, starting at YYNTOKENS, nonterminals.  */
static const char *const yytname[] =
{
  "$end", "error", "$undefined", "'{'", "')'", "WORD", "LABEL", "APPEND",
  "OPEN", "SSELECT", "LOOPEX", "DOTDOT", "USING", "FORMAT", "DO", "SHIFT",
  "PUSH", "POP", "LVALFUN", "WHILE", "UNTIL", "IF", "UNLESS", "ELSE",
  "ELSIF", "CONTINUE", "SPLIT", "FLIST", "FOR", "FILOP", "FILOP2",
  "FILOP3", "FILOP4", "FILOP22", "FILOP25", "FUNC0", "FUNC1", "FUNC2",
  "FUNC2x", "FUNC3", "FUNC4", "FUNC5", "HSHFUN", "HSHFUN3", "FLIST2",
  "SUB", "FILETEST", "LOCAL", "DELETE", "RELOP", "EQOP", "MULOP", "ADDOP",
  "PACKAGE", "AMPER", "FORMLIST", "REG", "ARYLEN", "ARY", "HSH", "STAR",
  "SUBST", "PATTERN", "RSTRING", "TRANS", "LISTOP", "','", "'='", "'?'",
  "':'", "OROR", "ANDAND", "'|'", "'^'", "'&'", "UNIOP", "LS", "RS",
  "MATCH", "NMATCH", "'!'", "'~'", "UMINUS", "POW", "INC", "DEC", "'('",
  "'}'", "';'", "'-'", "'+'", "'['", "']'", "$accept", "prog", "$@1",
  "compblock", "else", "block", "remember", "lineseq", "line", "sideff",
  "cond", "loop", "nexpr", "texpr", "label", "decl", "format", "subrout",
  "package", "cexpr", "expr", "csexpr", "sexpr", "term", "listop",
  "handle", "aryword", "hshword", "crp", "bareword", YY_NULLPTR
};
#endif

# ifdef YYPRINT
/* YYTOKNUM[NUM] -- (External) token number corresponding to the
   (internal) symbol number NUM (which must be that of a token).  */
static const yytype_uint16 yytoknum[] =
{
       0,   256,   257,   123,    41,   258,   259,   260,   261,   262,
     263,   264,   265,   266,   267,   268,   269,   270,   271,   272,
     273,   274,   275,   276,   277,   278,   279,   280,   281,   282,
     283,   284,   285,   286,   287,   288,   289,   290,   291,   292,
     293,   294,   295,   296,   297,   298,   299,   300,   301,   302,
     303,   304,   305,   306,   307,   308,   309,   310,   311,   312,
     313,   314,   315,   316,   317,   318,    44,    61,    63,    58,
     319,   320,   124,    94,    38,   321,   322,   323,   324,   325,
      33,   126,   326,   327,   328,   329,    40,   125,    59,    45,
      43,    91,    93
};
# endif

#define YYPACT_NINF -165

#define yypact_value_is_default(Yystate) \
  (!!((Yystate) == (-165)))

#define YYTABLE_NINF -180

#define yytable_value_is_error(Yytable_value) \
  (!!((Yytable_value) == (-180)))

  /* YYPACT[STATE-NUM] -- Index in YYTABLE of the portion describing
     STATE-NUM.  */
static const yytype_int16 yypact[] =
{
    -165,    22,  -165,  -165,   133,  -165,    15,    42,    81,  -165,
    -165,   438,  -165,  -165,  -165,  -165,   -41,    59,    36,    57,
    -165,  -165,  -165,    37,    40,   125,   791,    33,    58,    55,
    2378,     1,     7,     9,    34,    91,    98,   -29,    38,   106,
     121,   140,   152,   154,   155,   157,   159,   161,   162,   164,
     167,    56,   169,   171,  2461,   173,   -27,    41,    14,  -165,
      21,  -165,  -165,  -165,  -165,  -165,  -165,   879,   967,  2378,
    2378,  2378,  2378,  1050,  -165,  2378,  2378,  -165,   165,   131,
    -165,   201,  2753,   -66,  -165,  -165,   177,  -165,  -165,  -165,
    -165,  -165,  2544,  -165,  2627,  -165,   175,    -2,  -165,   469,
    -165,  -165,   127,  -165,   127,   127,  -165,  -165,  2378,    36,
    2378,    36,  2378,    36,  2378,    36,  2378,  2378,   182,   524,
    -165,  -165,  1133,  2627,  2627,  2627,  2627,  2627,   233,  1216,
    2378,  2378,  2378,  2378,  2378,  -165,  -165,    20,  -165,    20,
    2378,  -165,   469,  2378,   248,   207,   183,   186,  2378,  2378,
    2378,  2378,  2378,   702,  2378,   208,  -165,   469,   -66,   -66,
     -20,   -20,   185,    17,   -66,   -66,    36,   187,    36,  -165,
    -165,  2378,  2378,  2378,  2378,  2378,  2378,  2378,  2378,  1714,
    1797,  2378,  2378,  2378,  2378,  1880,  1963,  2046,  2129,  2212,
    2378,  2378,  2295,  -165,  -165,  -165,    35,    19,  2753,   211,
      24,  2669,   275,  1299,  1382,   281,   221,   284,   287,   208,
    -165,    51,  -165,    65,  -165,    71,  -165,   267,    17,  2378,
    -165,   204,    52,  -165,   289,   211,   230,   230,   235,   237,
    -165,  -165,    72,  2704,  2669,  2669,  2669,  2669,   301,   230,
    2704,    17,  2378,   305,  1465,  1548,   116,   -26,   130,    83,
     208,   208,   208,  2378,  -165,  1631,   220,  -165,  2378,  -165,
     208,   208,   208,   208,  2753,   351,  2839,  2717,  2378,   -16,
    2378,   146,  2753,  2739,   334,  2796,  2378,  2648,  2378,  2648,
    2378,  2831,  2378,   231,  2378,   231,   229,   229,  2378,   229,
    -165,  -165,  2378,   309,  2378,   230,  -165,  -165,    17,  -165,
      17,  -165,  2378,  -165,    36,    36,    36,    36,  -165,    74,
    -165,    17,  2378,    36,  -165,   317,   211,   230,  2627,  2627,
    -165,   323,    77,   211,   230,   230,  -165,   211,   324,  -165,
     142,  2378,  -165,    17,  -165,    17,   244,  -165,   245,  -165,
     107,  -165,  2378,   117,  2753,  2753,  2378,  2753,  2753,  2753,
    2753,  2753,  2753,   208,  -165,  2753,   230,  -165,  -165,    17,
    -165,  -165,  -165,  -165,  -165,   332,    36,   259,  -165,  -165,
     338,   211,   344,   230,  -165,  -165,   345,   347,   211,   230,
     348,  -165,   266,   151,  -165,  -165,  -165,  -165,  -165,   114,
      36,  2788,   352,  -165,  -165,  -165,   614,  -165,   353,  -165,
     230,  -165,  -165,   355,   211,  -165,  -165,   268,  -165,  -165,
    -165,   356,  -165,   211,  -165,   357,   359,    36,   361,  -165,
    -165,  -165,  -165
};

  /* YYDEFACT[STATE-NUM] -- Default reduction number in state STATE-NUM.
     Performed when YYTABLE does not specify something else to do.  Zero
     means the default is an error.  */
static const yytype_uint8 yydefact[] =
{
       2,     0,    11,     1,    40,    41,     0,     0,     0,    12,
      15,     0,    13,    42,    43,    44,     0,     0,     0,     0,
      18,    10,   187,     0,   127,   122,     0,   149,     0,     0,
     157,     0,     0,     0,     0,   150,     0,     0,   138,     0,
       0,     0,     0,     0,   158,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    89,     0,     0,     0,    95,   107,
      99,    98,    96,   110,   109,   108,   111,   173,   124,     0,
       0,     0,     0,     0,    16,     0,     0,    35,     6,     0,
      14,    19,    51,    78,   172,   171,     0,    46,    47,    48,
      11,   131,     0,   128,     0,   123,   187,    95,    94,    93,
     181,   182,     0,   147,     0,     0,   145,   156,    38,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     135,   136,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,   183,   184,     0,   169,     0,
       0,    87,    88,     0,     0,     0,   116,   121,     0,     0,
       0,     0,   175,    95,     0,   174,   125,   126,    81,    82,
      85,    86,    92,     0,    79,    80,     0,     0,     0,     5,
      17,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    83,    84,    45,    40,   187,   180,     0,
     187,   180,     0,     0,     0,     0,     0,     0,     0,    39,
      30,     0,    31,     0,    26,     0,    27,     0,     0,     0,
      37,     0,    19,   137,     0,     0,     0,     0,     0,     0,
     159,   160,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
     176,   177,   178,     0,   186,     0,    91,     7,     0,     4,
      22,    23,    20,    21,    50,    72,    67,    68,     0,    63,
       0,    64,    53,     0,    74,    73,     0,    71,     0,    70,
       0,    69,     0,    65,     0,    66,    76,    77,     0,    62,
       9,   132,     0,     0,     0,     0,   129,   114,     0,   119,
       0,   148,     0,   146,     0,     0,     0,     0,   153,     0,
     155,     0,    38,     0,   134,     0,     0,     0,     0,     0,
     161,     0,     0,     0,     0,     0,   168,     0,     0,    90,
       0,     0,   115,     0,   120,     0,     0,    97,     0,   103,
       0,   185,     0,     0,    55,    56,     0,    61,    60,    59,
      57,    58,    54,    49,   133,    52,     0,   112,   117,     0,
      28,    29,    24,    25,   152,     0,     0,     0,    33,   139,
       0,     0,     0,     0,   162,   163,     0,     0,     0,     0,
       0,   154,     0,     0,   113,   118,   100,   104,   102,     0,
       0,    75,     0,   144,   151,    32,     0,   140,     0,   141,
       0,   164,   165,     0,     0,   170,   105,     0,   101,     8,
     130,     0,   142,     0,   166,     0,     0,     0,     0,   167,
     106,    34,   143
};

  /* YYPGOTO[NTERM-NUM].  */
static const yytype_int16 yypgoto[] =
{
    -165,  -165,  -165,  -104,  -165,   -18,  -165,   276,  -165,   358,
    -165,  -165,   -28,    61,  -165,  -165,  -165,  -165,  -165,  -129,
      62,    63,   -24,   254,  -165,   -91,    23,   -86,  -164,  -165
};

  /* YYDEFGOTO[NTERM-NUM].  */
static const yytype_int16 yydefgoto[] =
{
      -1,     1,     2,    77,   169,    78,    90,     4,     9,   220,
      80,    10,   221,   208,    11,    12,    13,    14,    15,   293,
      81,   295,    82,    83,    84,   199,   103,   138,   256,    85
};

  /* YYTABLE[YYPACT[STATE-NUM]] -- What to do in state STATE-NUM.  If
     positive, shift that token.  If negative, reduce the rule whose
     number is the opposite.  If YYTABLE_NINF, syntax error.  */
static const yytype_int16 yytable[] =
{
      88,   148,    99,   202,    21,   210,   107,   212,    98,   214,
      21,   216,    21,   109,   111,   113,   115,   148,   193,   194,
      16,   254,     3,   291,   150,   135,    86,   118,  -179,   144,
     142,   224,   225,   226,   227,   228,   229,    21,   100,    21,
     175,     5,    91,   120,   157,    93,   146,    18,     6,   154,
     156,   238,   106,   239,   310,   305,   254,   119,   313,   145,
     100,   135,   190,   191,  -180,  -180,   337,   192,   198,   306,
     201,   171,   172,   173,   174,   307,   320,   329,   364,   136,
       7,   375,    17,   255,   204,  -179,    19,   108,     8,   149,
    -179,   101,   217,   110,   121,   112,   315,   147,   198,   198,
     198,   198,   198,   198,   321,   149,   233,   234,   235,   236,
     237,   328,   151,   101,    87,   136,   240,   175,   255,   102,
     114,   390,   290,    92,   122,   205,    94,   206,   207,   155,
      95,   175,   100,    -3,   357,   163,   358,   175,   175,     5,
     294,   105,   137,   292,   104,    89,     6,   366,   257,   175,
     259,   264,   265,   266,   267,   269,   271,   272,   273,   274,
     275,   277,   279,   281,   283,   285,   286,   287,   289,   384,
     209,   385,   211,   175,   213,   339,   215,   116,     7,   218,
     175,   222,   175,   175,   117,   101,     8,   370,   166,   167,
     168,   232,   123,   376,   377,   393,   175,   179,   380,   388,
     360,   361,   362,   363,   336,   241,   408,   124,   175,   368,
     246,   247,   248,   249,   250,   251,   252,   175,   338,   170,
     171,   172,   173,   174,   190,   191,   125,   372,   373,   192,
     382,   264,   195,   260,   261,   262,   263,   230,   126,   407,
     127,   128,   398,   129,   344,   130,   345,   131,   132,   403,
     133,   242,   347,   134,   348,   139,   349,   140,   350,   143,
     351,   203,   395,   243,   352,   298,   300,   175,   219,   244,
     355,   308,   245,   258,   175,   415,   253,   292,   176,   296,
     309,   311,   179,   180,   418,   301,   409,   302,   303,   316,
     317,   304,   312,   314,   198,   198,   294,   322,   323,   324,
     325,   318,   327,   319,   330,   326,   333,   335,   331,   190,
     191,   342,   192,   354,   192,   340,   177,   178,   179,   180,
     343,   369,   391,   158,   159,   160,   161,   374,   381,   164,
     165,   386,   387,   294,   181,   182,   394,   183,   184,   185,
     186,   187,   397,   188,   189,   190,   191,   396,   399,   401,
     192,   402,   405,   406,   353,   416,   410,   412,   356,   414,
     417,   419,  -180,   420,   359,   422,   196,     0,   411,    79,
       0,     0,   365,   367,   209,     0,     0,     0,     0,     0,
     371,     0,     0,   177,   178,   179,   180,   378,   379,     0,
       0,     0,     0,   383,     0,     0,     0,     0,     0,   421,
     177,   178,   179,   180,   389,   184,   185,   186,   187,     0,
     188,   189,   190,   191,     0,     0,     0,   192,     0,   392,
       0,   183,   184,   185,   186,   187,     0,   188,   189,   190,
     191,     0,     0,     0,   192,     0,   400,     0,     0,    20,
       0,    21,   404,    22,     0,     0,    23,    24,    25,     0,
       0,     0,    26,    27,    28,    29,    30,    31,    32,    33,
      34,     0,     0,   413,    35,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    45,    46,    47,    48,    49,    50,
      51,    52,    53,     0,    54,    55,    56,     0,     0,     0,
       0,     0,    57,     0,    58,    59,    60,    61,    62,    63,
      64,    65,    66,    67,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    68,     0,     0,     0,     0,    69,    70,
     179,   180,    71,    72,    73,    20,    74,    75,    76,    22,
       0,     0,    23,    24,    25,     0,     0,     0,    26,    27,
      28,    29,    30,     0,     0,   188,   189,   190,   191,     0,
      35,    36,   192,    38,    39,    40,    41,    42,    43,    44,
      45,    46,    47,    48,    49,    50,    51,    52,    53,     0,
      54,    55,    56,     0,     0,     0,     0,     0,    57,     0,
      58,    59,    60,    61,    62,    63,    64,    65,    66,    67,
       0,     0,     0,     0,     0,     0,     0,     0,     0,    68,
       0,     0,     0,     0,    69,    70,     0,     0,    71,    72,
      73,     0,   -36,    75,    76,    20,     0,     0,   -36,    22,
       0,     0,    23,    24,    25,     0,     0,     0,    26,    27,
      28,    29,    30,     0,     0,     0,     0,     0,     0,     0,
      35,    36,     0,    38,    39,    40,    41,    42,    43,    44,
      45,    46,    47,    48,    49,    50,    51,    52,    53,     0,
      54,    55,    56,     0,     0,     0,     0,     0,    57,     0,
      58,    59,    60,    61,    62,    63,    64,    65,    66,    67,
       0,     0,     0,     0,     0,     0,     0,     0,     0,    68,
       0,     0,     0,     0,    69,    70,     0,     0,    71,    72,
      73,     0,     0,    75,    76,   148,     0,    22,     0,     0,
      23,    24,    25,     0,     0,     0,    26,    27,    28,    29,
      30,     0,     0,     0,     0,     0,     0,     0,    35,    36,
       0,    38,    39,    40,    41,    42,    43,    44,    45,    46,
      47,    48,    49,    50,    51,    52,    53,     0,    54,    55,
      56,     0,     0,     0,     0,     0,    57,     0,    58,    59,
      60,    61,    62,    63,    64,    65,    66,    67,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    68,     0,     0,
       0,     0,    69,    70,     0,     0,     0,     0,    73,     0,
       0,    75,    76,   149,    21,     0,    96,     0,     0,    23,
      24,    25,     0,     0,     0,    26,    27,    28,    29,    30,
       0,     0,     0,     0,     0,     0,     0,    35,    36,     0,
      38,    39,    40,    41,    42,    43,    44,    45,    46,    47,
      48,    49,    50,    51,    52,    53,     0,    54,    55,    56,
       0,     0,     0,     0,     0,    57,     0,    97,    59,    60,
      61,    62,    63,    64,    65,    66,    67,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    68,     0,     0,     0,
       0,    69,    70,     0,     0,    71,    72,    73,     0,     0,
      75,    76,    21,     0,   152,     0,     0,    23,    24,    25,
       0,     0,     0,    26,    27,    28,    29,    30,     0,     0,
       0,     0,     0,     0,     0,    35,    36,     0,    38,    39,
      40,    41,    42,    43,    44,    45,    46,    47,    48,    49,
      50,    51,    52,    53,     0,    54,    55,    56,     0,     0,
       0,     0,     0,    57,     0,   153,    59,    60,    61,    62,
      63,    64,    65,    66,    67,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    68,     0,     0,     0,     0,    69,
      70,     0,     0,    71,    72,    73,     0,     0,    75,    76,
      21,     0,    22,     0,     0,    23,    24,    25,     0,     0,
       0,    26,    27,    28,    29,    30,     0,     0,     0,     0,
       0,     0,     0,    35,    36,     0,    38,    39,    40,    41,
      42,    43,    44,    45,    46,    47,    48,    49,    50,    51,
      52,    53,     0,    54,    55,    56,     0,     0,     0,     0,
       0,    57,     0,    58,    59,    60,    61,    62,    63,    64,
      65,    66,    67,     0,     0,     0,     0,     0,     0,     0,
       0,     0,    68,     0,     0,     0,     0,    69,    70,     0,
       0,    71,    72,    73,   162,    22,    75,    76,    23,    24,
      25,     0,     0,     0,    26,    27,    28,    29,    30,     0,
       0,     0,     0,     0,     0,     0,    35,    36,     0,    38,
      39,    40,    41,    42,    43,    44,    45,    46,    47,    48,
      49,    50,    51,    52,    53,     0,    54,    55,    56,     0,
       0,     0,     0,     0,    57,     0,    58,    59,    60,    61,
      62,    63,    64,    65,    66,    67,     0,     0,     0,     0,
       0,     0,     0,     0,     0,    68,     0,     0,     0,     0,
      69,    70,     0,     0,    71,    72,    73,   223,   200,    75,
      76,    23,    24,    25,     0,     0,     0,    26,    27,    28,
      29,    30,     0,     0,     0,     0,     0,     0,     0,    35,
      36,     0,    38,    39,    40,    41,    42,    43,    44,    45,
      46,    47,    48,    49,    50,    51,    52,    53,     0,    54,
      55,    56,     0,     0,     0,     0,     0,    57,     0,    58,
      59,    60,    61,    62,    63,    64,    65,    66,    67,     0,
       0,     0,     0,     0,     0,     0,     0,     0,    68,     0,
       0,     0,     0,    69,    70,     0,     0,    71,    72,    73,
     231,    22,    75,    76,    23,    24,    25,     0,     0,     0,
      26,    27,    28,    29,    30,     0,     0,     0,     0,     0,
       0,     0,    35,    36,     0,    38,    39,    40,    41,    42,
      43,    44,    45,    46,    47,    48,    49,    50,    51,    52,
      53,     0,    54,    55,    56,     0,     0,     0,     0,     0,
      57,     0,    58,    59,    60,    61,    62,    63,    64,    65,
      66,    67,     0,     0,     0,     0,     0,     0,     0,     0,
       0,    68,     0,     0,     0,     0,    69,    70,     0,     0,
      71,    72,    73,   297,    22,    75,    76,    23,    24,    25,
       0,     0,     0,    26,    27,    28,    29,    30,     0,     0,
       0,     0,     0,     0,     0,    35,    36,     0,    38,    39,
      40,    41,    42,    43,    44,    45,    46,    47,    48,    49,
      50,    51,    52,    53,     0,    54,    55,    56,     0,     0,
       0,     0,     0,    57,     0,    58,    59,    60,    61,    62,
      63,    64,    65,    66,    67,     0,     0,     0,     0,     0,
       0,     0,     0,     0,    68,     0,     0,     0,     0,    69,
      70,     0,     0,    71,    72,    73,   299,    22,    75,    76,
      23,    24,    25,     0,     0,     0,    26,    27,    28,    29,
      30,     0,     0,     0,     0,     0,     0,     0,    35,    36,
       0,    38,    39,    40,    41,    42,    43,    44,    45,    46,
      47,    48,    49,    50,    51,    52,    53,     0,    54,    55,
      56,     0,     0,     0,     0,     0,    57,     0,    58,    59,
      60,    61,    62,    63,    64,    65,    66,    67,     0,     0,
       0,     0,     0,     0,     0,     0,     0,    68,     0,     0,
       0,     0,    69,    70,     0,     0,    71,    72,    73,   332,
      22,    75,    76,    23,    24,    25,     0,     0,     0,    26,
      27,    28,    29,    30,     0,     0,     0,     0,     0,     0,
       0,    35,    36,     0,    38,    39,    40,    41,    42,    43,
      44,    45,    46,    47,    48,    49,    50,    51,    52,    53,
       0,    54,    55,    56,     0,     0,     0,     0,     0,    57,
       0,    58,    59,    60,    61,    62,    63,    64,    65,    66,
      67,     0,     0,     0,     0,     0,     0,     0,     0,     0,
      68,     0,     0,     0,     0,    69,    70,     0,     0,    71,
      72,    73,   334,    22,    75,    76,    23,    24,    25,     0,
       0,     0,    26,    27,    28,    29,    30,     0,     0,     0,
       0,     0,     0,     0,    35,    36,     0,    38,    39,    40,
      41,    42,    43,    44,    45,    46,    47,    48,    49,    50,
      51,    52,    53,     0,    54,    55,    56,     0,     0,     0,
       0,     0,    57,     0,    58,    59,    60,    61,    62,    63,
      64,    65,    66,    67,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    68,     0,     0,     0,     0,    69,    70,
       0,     0,    71,    72,    73,   341,    22,    75,    76,    23,
      24,    25,     0,     0,     0,    26,    27,    28,    29,    30,
       0,     0,     0,     0,     0,     0,     0,    35,    36,     0,
      38,    39,    40,    41,    42,    43,    44,    45,    46,    47,
      48,    49,    50,    51,    52,    53,     0,    54,    55,    56,
       0,     0,     0,     0,     0,    57,     0,    58,    59,    60,
      61,    62,    63,    64,    65,    66,    67,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    68,     0,     0,     0,
       0,    69,    70,     0,     0,    71,    72,    73,     0,    22,
      75,    76,    23,    24,    25,     0,     0,     0,    26,    27,
      28,    29,    30,     0,     0,     0,     0,     0,     0,     0,
      35,    36,     0,    38,    39,    40,    41,    42,    43,    44,
      45,    46,    47,    48,    49,    50,    51,    52,    53,     0,
      54,    55,    56,     0,     0,     0,     0,     0,    57,     0,
      58,    59,    60,    61,    62,    63,    64,    65,    66,    67,
       0,   268,     0,     0,     0,     0,     0,     0,     0,    68,
       0,     0,     0,     0,    69,    70,     0,     0,    71,    72,
      73,     0,    22,    75,    76,    23,    24,    25,     0,     0,
       0,    26,    27,    28,    29,    30,     0,     0,     0,     0,
       0,     0,     0,    35,    36,     0,    38,    39,    40,    41,
      42,    43,    44,    45,    46,    47,    48,    49,    50,    51,
      52,    53,     0,    54,    55,    56,     0,     0,     0,     0,
       0,    57,     0,    58,    59,    60,    61,    62,    63,    64,
      65,    66,    67,     0,   270,     0,     0,     0,     0,     0,
       0,     0,    68,     0,     0,     0,     0,    69,    70,     0,
       0,    71,    72,    73,     0,    22,    75,    76,    23,    24,
      25,     0,     0,     0,    26,    27,    28,    29,    30,     0,
       0,     0,     0,     0,     0,     0,    35,    36,     0,    38,
      39,    40,    41,    42,    43,    44,    45,    46,    47,    48,
      49,    50,    51,    52,    53,     0,    54,    55,    56,     0,
       0,     0,     0,     0,    57,     0,    58,    59,    60,    61,
      62,    63,    64,    65,    66,    67,     0,   276,     0,     0,
       0,     0,     0,     0,     0,    68,     0,     0,     0,     0,
      69,    70,     0,     0,    71,    72,    73,     0,    22,    75,
      76,    23,    24,    25,     0,     0,     0,    26,    27,    28,
      29,    30,     0,     0,     0,     0,     0,     0,     0,    35,
      36,     0,    38,    39,    40,    41,    42,    43,    44,    45,
      46,    47,    48,    49,    50,    51,    52,    53,     0,    54,
      55,    56,     0,     0,     0,     0,     0,    57,     0,    58,
      59,    60,    61,    62,    63,    64,    65,    66,    67,     0,
     278,     0,     0,     0,     0,     0,     0,     0,    68,     0,
       0,     0,     0,    69,    70,     0,     0,    71,    72,    73,
       0,    22,    75,    76,    23,    24,    25,     0,     0,     0,
      26,    27,    28,    29,    30,     0,     0,     0,     0,     0,
       0,     0,    35,    36,     0,    38,    39,    40,    41,    42,
      43,    44,    45,    46,    47,    48,    49,    50,    51,    52,
      53,     0,    54,    55,    56,     0,     0,     0,     0,     0,
      57,     0,    58,    59,    60,    61,    62,    63,    64,    65,
      66,    67,     0,   280,     0,     0,     0,     0,     0,     0,
       0,    68,     0,     0,     0,     0,    69,    70,     0,     0,
      71,    72,    73,     0,    22,    75,    76,    23,    24,    25,
       0,     0,     0,    26,    27,    28,    29,    30,     0,     0,
       0,     0,     0,     0,     0,    35,    36,     0,    38,    39,
      40,    41,    42,    43,    44,    45,    46,    47,    48,    49,
      50,    51,    52,    53,     0,    54,    55,    56,     0,     0,
       0,     0,     0,    57,     0,    58,    59,    60,    61,    62,
      63,    64,    65,    66,    67,     0,   282,     0,     0,     0,
       0,     0,     0,     0,    68,     0,     0,     0,     0,    69,
      70,     0,     0,    71,    72,    73,     0,    22,    75,    76,
      23,    24,    25,     0,     0,     0,    26,    27,    28,    29,
      30,     0,     0,     0,     0,     0,     0,     0,    35,    36,
       0,    38,    39,    40,    41,    42,    43,    44,    45,    46,
      47,    48,    49,    50,    51,    52,    53,     0,    54,    55,
      56,     0,     0,     0,     0,     0,    57,     0,    58,    59,
      60,    61,    62,    63,    64,    65,    66,    67,     0,   284,
       0,     0,     0,     0,     0,     0,     0,    68,     0,     0,
       0,     0,    69,    70,     0,     0,    71,    72,    73,     0,
      22,    75,    76,    23,    24,    25,     0,     0,     0,    26,
      27,    28,    29,    30,     0,     0,     0,     0,     0,     0,
       0,    35,    36,     0,    38,    39,    40,    41,    42,    43,
      44,    45,    46,    47,    48,    49,    50,    51,    52,    53,
       0,    54,    55,    56,     0,     0,     0,     0,     0,    57,
       0,    58,    59,    60,    61,    62,    63,    64,    65,    66,
      67,     0,   288,     0,     0,     0,     0,     0,     0,     0,
      68,     0,     0,     0,     0,    69,    70,     0,     0,    71,
      72,    73,     0,    22,    75,    76,    23,    24,    25,     0,
       0,     0,    26,    27,    28,    29,    30,     0,     0,     0,
       0,     0,     0,     0,    35,    36,     0,    38,    39,    40,
      41,    42,    43,    44,    45,    46,    47,    48,    49,    50,
      51,    52,    53,     0,    54,    55,    56,     0,     0,     0,
       0,     0,    57,     0,    58,    59,    60,    61,    62,    63,
      64,    65,    66,    67,     0,     0,     0,     0,     0,     0,
       0,     0,     0,    68,     0,     0,     0,     0,    69,    70,
       0,     0,    71,    72,    73,     0,   141,    75,    76,    23,
      24,    25,     0,     0,     0,    26,    27,    28,    29,    30,
       0,     0,     0,     0,     0,     0,     0,    35,    36,     0,
      38,    39,    40,    41,    42,    43,    44,    45,    46,    47,
      48,    49,    50,    51,    52,    53,     0,    54,    55,    56,
       0,     0,     0,     0,     0,    57,     0,    58,    59,    60,
      61,    62,    63,    64,    65,    66,    67,     0,     0,     0,
       0,     0,     0,     0,     0,     0,    68,     0,     0,     0,
       0,    69,    70,     0,     0,    71,    72,    73,     0,   197,
      75,    76,    23,    24,    25,     0,     0,     0,    26,    27,
      28,    29,    30,     0,     0,     0,     0,     0,     0,     0,
      35,    36,     0,    38,    39,    40,    41,    42,    43,    44,
      45,    46,    47,    48,    49,    50,    51,    52,    53,     0,
      54,    55,    56,     0,     0,     0,     0,     0,    57,     0,
      58,    59,    60,    61,    62,    63,    64,    65,    66,    67,
       0,     0,     0,     0,     0,     0,     0,     0,     0,    68,
       0,     0,     0,     0,    69,    70,     0,     0,    71,    72,
      73,     0,   200,    75,    76,    23,    24,    25,     0,     0,
       0,    26,    27,    28,    29,    30,     0,     0,     0,     0,
       0,     0,     0,    35,    36,     0,    38,    39,    40,    41,
      42,    43,    44,    45,    46,    47,    48,    49,    50,    51,
      52,    53,     0,    54,    55,    56,     0,     0,     0,     0,
     176,    57,     0,    58,    59,    60,    61,    62,    63,    64,
      65,    66,    67,     0,     0,     0,     0,   177,   178,   179,
     180,     0,    68,     0,     0,     0,     0,    69,    70,     0,
       0,    71,    72,    73,     0,   176,    75,    76,   177,   178,
     179,   180,   187,     0,   188,   189,   190,   191,     0,     0,
       0,   192,     0,     0,     0,   294,   181,   182,     0,   183,
     184,   185,   186,   187,     0,   188,   189,   190,   191,     0,
     176,     0,   192,   177,   178,   179,   180,     0,     0,     0,
       0,     0,     0,     0,   176,     0,   177,  -180,   179,   180,
     292,   181,   182,     0,   183,   184,   185,   186,   187,     0,
     188,   189,   190,   191,     0,     0,     0,   192,   177,   178,
     179,   180,     0,   188,   189,   190,   191,     0,     0,   176,
     192,     0,   177,   178,   179,   180,   181,   182,   346,   183,
     184,   185,   186,   187,     0,   188,   189,   190,   191,     0,
     181,   182,   192,   183,   184,   185,   186,   187,     0,   188,
     189,   190,   191,     0,     0,     0,   192,   177,   178,   179,
     180,     0,     0,     0,     0,   177,   178,   179,   180,     0,
       0,     0,     0,     0,     0,     0,   182,     0,   183,   184,
     185,   186,   187,     0,   188,   189,   190,   191,   185,   186,
     187,   192,   188,   189,   190,   191,     0,     0,     0,   192,
     177,   178,   179,   180,     0,     0,     0,     0,  -180,     0,
     179,   180,     0,     0,     0,     0,     0,     0,     0,     0,
       0,     0,     0,     0,     0,     0,     0,   188,   189,   190,
     191,     0,     0,     0,   192,   188,   189,   190,   191,     0,
       0,     0,   192
};

static const yytype_int16 yycheck[] =
{
      18,     3,    26,    94,     3,   109,    30,   111,    26,   113,
       3,   115,     3,    31,    32,    33,    34,     3,    84,    85,
       5,     4,     0,     4,     3,     5,    67,    56,     4,    56,
      54,   122,   123,   124,   125,   126,   127,     3,     5,     3,
      66,     6,     5,     5,    68,     5,     5,     5,    13,    67,
      68,   137,    29,   139,   218,     4,     4,    86,   222,    86,
       5,     5,    78,    79,    84,    85,    92,    83,    92,     4,
      94,    19,    20,    21,    22,     4,     4,   241,     4,    59,
      45,     4,    67,    66,    86,    66,     5,    86,    53,    91,
      66,    58,   116,    86,    56,    86,   225,    56,   122,   123,
     124,   125,   126,   127,   233,    91,   130,   131,   132,   133,
     134,   240,    91,    58,    55,    59,   140,    66,    66,    86,
      86,     4,    87,    86,    86,   102,    86,   104,   105,    67,
       5,    66,     5,     0,   298,    73,   300,    66,    66,     6,
      66,    86,    86,    66,    86,    88,    13,   311,   166,    66,
     168,   175,   176,   177,   178,   179,   180,   181,   182,   183,
     184,   185,   186,   187,   188,   189,   190,   191,   192,   333,
     108,   335,   110,    66,   112,    92,   114,    86,    45,   117,
      66,   119,    66,    66,    86,    58,    53,   316,    23,    24,
      25,   129,    86,   322,   323,   359,    66,    51,   327,    92,
     304,   305,   306,   307,    88,   143,    92,    86,    66,   313,
     148,   149,   150,   151,   152,   153,   154,    66,    88,    88,
      19,    20,    21,    22,    78,    79,    86,   318,   319,    83,
      88,   255,    55,   171,   172,   173,   174,     4,    86,    88,
      86,    86,   371,    86,   268,    86,   270,    86,    86,   378,
      86,     3,   276,    86,   278,    86,   280,    86,   282,    86,
     284,    86,   366,    56,   288,   203,   204,    66,    86,    86,
     294,     4,    86,    86,    66,   404,    91,    66,    11,     4,
     217,   219,    51,    52,   413,     4,   390,    66,     4,   226,
     227,     4,    88,     4,   318,   319,    66,   234,   235,   236,
     237,    66,   239,    66,   242,     4,   244,   245,     3,    78,
      79,    91,    83,     4,    83,   253,    49,    50,    51,    52,
     258,     4,   346,    69,    70,    71,    72,     4,     4,    75,
      76,    87,    87,    66,    67,    68,     4,    70,    71,    72,
      73,    74,     4,    76,    77,    78,    79,    88,     4,     4,
      83,     4,     4,    87,   292,    87,     4,     4,   295,     4,
       4,     4,    11,     4,   302,     4,    90,    -1,   396,    11,
      -1,    -1,   309,   312,   312,    -1,    -1,    -1,    -1,    -1,
     317,    -1,    -1,    49,    50,    51,    52,   324,   325,    -1,
      -1,    -1,    -1,   331,    -1,    -1,    -1,    -1,    -1,   417,
      49,    50,    51,    52,   342,    71,    72,    73,    74,    -1,
      76,    77,    78,    79,    -1,    -1,    -1,    83,    -1,   356,
      -1,    70,    71,    72,    73,    74,    -1,    76,    77,    78,
      79,    -1,    -1,    -1,    83,    -1,   373,    -1,    -1,     1,
      -1,     3,   379,     5,    -1,    -1,     8,     9,    10,    -1,
      -1,    -1,    14,    15,    16,    17,    18,    19,    20,    21,
      22,    -1,    -1,   400,    26,    27,    28,    29,    30,    31,
      32,    33,    34,    35,    36,    37,    38,    39,    40,    41,
      42,    43,    44,    -1,    46,    47,    48,    -1,    -1,    -1,
      -1,    -1,    54,    -1,    56,    57,    58,    59,    60,    61,
      62,    63,    64,    65,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,    80,    81,
      51,    52,    84,    85,    86,     1,    88,    89,    90,     5,
      -1,    -1,     8,     9,    10,    -1,    -1,    -1,    14,    15,
      16,    17,    18,    -1,    -1,    76,    77,    78,    79,    -1,
      26,    27,    83,    29,    30,    31,    32,    33,    34,    35,
      36,    37,    38,    39,    40,    41,    42,    43,    44,    -1,
      46,    47,    48,    -1,    -1,    -1,    -1,    -1,    54,    -1,
      56,    57,    58,    59,    60,    61,    62,    63,    64,    65,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,
      -1,    -1,    -1,    -1,    80,    81,    -1,    -1,    84,    85,
      86,    -1,    88,    89,    90,     1,    -1,    -1,     4,     5,
      -1,    -1,     8,     9,    10,    -1,    -1,    -1,    14,    15,
      16,    17,    18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      26,    27,    -1,    29,    30,    31,    32,    33,    34,    35,
      36,    37,    38,    39,    40,    41,    42,    43,    44,    -1,
      46,    47,    48,    -1,    -1,    -1,    -1,    -1,    54,    -1,
      56,    57,    58,    59,    60,    61,    62,    63,    64,    65,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,
      -1,    -1,    -1,    -1,    80,    81,    -1,    -1,    84,    85,
      86,    -1,    -1,    89,    90,     3,    -1,     5,    -1,    -1,
       8,     9,    10,    -1,    -1,    -1,    14,    15,    16,    17,
      18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,    27,
      -1,    29,    30,    31,    32,    33,    34,    35,    36,    37,
      38,    39,    40,    41,    42,    43,    44,    -1,    46,    47,
      48,    -1,    -1,    -1,    -1,    -1,    54,    -1,    56,    57,
      58,    59,    60,    61,    62,    63,    64,    65,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,
      -1,    -1,    80,    81,    -1,    -1,    -1,    -1,    86,    -1,
      -1,    89,    90,    91,     3,    -1,     5,    -1,    -1,     8,
       9,    10,    -1,    -1,    -1,    14,    15,    16,    17,    18,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,    27,    -1,
      29,    30,    31,    32,    33,    34,    35,    36,    37,    38,
      39,    40,    41,    42,    43,    44,    -1,    46,    47,    48,
      -1,    -1,    -1,    -1,    -1,    54,    -1,    56,    57,    58,
      59,    60,    61,    62,    63,    64,    65,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,
      -1,    80,    81,    -1,    -1,    84,    85,    86,    -1,    -1,
      89,    90,     3,    -1,     5,    -1,    -1,     8,     9,    10,
      -1,    -1,    -1,    14,    15,    16,    17,    18,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    26,    27,    -1,    29,    30,
      31,    32,    33,    34,    35,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    -1,    46,    47,    48,    -1,    -1,
      -1,    -1,    -1,    54,    -1,    56,    57,    58,    59,    60,
      61,    62,    63,    64,    65,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,    80,
      81,    -1,    -1,    84,    85,    86,    -1,    -1,    89,    90,
       3,    -1,     5,    -1,    -1,     8,     9,    10,    -1,    -1,
      -1,    14,    15,    16,    17,    18,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    26,    27,    -1,    29,    30,    31,    32,
      33,    34,    35,    36,    37,    38,    39,    40,    41,    42,
      43,    44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,
      -1,    54,    -1,    56,    57,    58,    59,    60,    61,    62,
      63,    64,    65,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    75,    -1,    -1,    -1,    -1,    80,    81,    -1,
      -1,    84,    85,    86,     4,     5,    89,    90,     8,     9,
      10,    -1,    -1,    -1,    14,    15,    16,    17,    18,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    26,    27,    -1,    29,
      30,    31,    32,    33,    34,    35,    36,    37,    38,    39,
      40,    41,    42,    43,    44,    -1,    46,    47,    48,    -1,
      -1,    -1,    -1,    -1,    54,    -1,    56,    57,    58,    59,
      60,    61,    62,    63,    64,    65,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,
      80,    81,    -1,    -1,    84,    85,    86,     4,     5,    89,
      90,     8,     9,    10,    -1,    -1,    -1,    14,    15,    16,
      17,    18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,
      27,    -1,    29,    30,    31,    32,    33,    34,    35,    36,
      37,    38,    39,    40,    41,    42,    43,    44,    -1,    46,
      47,    48,    -1,    -1,    -1,    -1,    -1,    54,    -1,    56,
      57,    58,    59,    60,    61,    62,    63,    64,    65,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,
      -1,    -1,    -1,    80,    81,    -1,    -1,    84,    85,    86,
       4,     5,    89,    90,     8,     9,    10,    -1,    -1,    -1,
      14,    15,    16,    17,    18,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    26,    27,    -1,    29,    30,    31,    32,    33,
      34,    35,    36,    37,    38,    39,    40,    41,    42,    43,
      44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,    -1,
      54,    -1,    56,    57,    58,    59,    60,    61,    62,    63,
      64,    65,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    75,    -1,    -1,    -1,    -1,    80,    81,    -1,    -1,
      84,    85,    86,     4,     5,    89,    90,     8,     9,    10,
      -1,    -1,    -1,    14,    15,    16,    17,    18,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    26,    27,    -1,    29,    30,
      31,    32,    33,    34,    35,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    -1,    46,    47,    48,    -1,    -1,
      -1,    -1,    -1,    54,    -1,    56,    57,    58,    59,    60,
      61,    62,    63,    64,    65,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,    80,
      81,    -1,    -1,    84,    85,    86,     4,     5,    89,    90,
       8,     9,    10,    -1,    -1,    -1,    14,    15,    16,    17,
      18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,    27,
      -1,    29,    30,    31,    32,    33,    34,    35,    36,    37,
      38,    39,    40,    41,    42,    43,    44,    -1,    46,    47,
      48,    -1,    -1,    -1,    -1,    -1,    54,    -1,    56,    57,
      58,    59,    60,    61,    62,    63,    64,    65,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,
      -1,    -1,    80,    81,    -1,    -1,    84,    85,    86,     4,
       5,    89,    90,     8,     9,    10,    -1,    -1,    -1,    14,
      15,    16,    17,    18,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    26,    27,    -1,    29,    30,    31,    32,    33,    34,
      35,    36,    37,    38,    39,    40,    41,    42,    43,    44,
      -1,    46,    47,    48,    -1,    -1,    -1,    -1,    -1,    54,
      -1,    56,    57,    58,    59,    60,    61,    62,    63,    64,
      65,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      75,    -1,    -1,    -1,    -1,    80,    81,    -1,    -1,    84,
      85,    86,     4,     5,    89,    90,     8,     9,    10,    -1,
      -1,    -1,    14,    15,    16,    17,    18,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    26,    27,    -1,    29,    30,    31,
      32,    33,    34,    35,    36,    37,    38,    39,    40,    41,
      42,    43,    44,    -1,    46,    47,    48,    -1,    -1,    -1,
      -1,    -1,    54,    -1,    56,    57,    58,    59,    60,    61,
      62,    63,    64,    65,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,    80,    81,
      -1,    -1,    84,    85,    86,     4,     5,    89,    90,     8,
       9,    10,    -1,    -1,    -1,    14,    15,    16,    17,    18,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,    27,    -1,
      29,    30,    31,    32,    33,    34,    35,    36,    37,    38,
      39,    40,    41,    42,    43,    44,    -1,    46,    47,    48,
      -1,    -1,    -1,    -1,    -1,    54,    -1,    56,    57,    58,
      59,    60,    61,    62,    63,    64,    65,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,
      -1,    80,    81,    -1,    -1,    84,    85,    86,    -1,     5,
      89,    90,     8,     9,    10,    -1,    -1,    -1,    14,    15,
      16,    17,    18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      26,    27,    -1,    29,    30,    31,    32,    33,    34,    35,
      36,    37,    38,    39,    40,    41,    42,    43,    44,    -1,
      46,    47,    48,    -1,    -1,    -1,    -1,    -1,    54,    -1,
      56,    57,    58,    59,    60,    61,    62,    63,    64,    65,
      -1,    67,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,
      -1,    -1,    -1,    -1,    80,    81,    -1,    -1,    84,    85,
      86,    -1,     5,    89,    90,     8,     9,    10,    -1,    -1,
      -1,    14,    15,    16,    17,    18,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    26,    27,    -1,    29,    30,    31,    32,
      33,    34,    35,    36,    37,    38,    39,    40,    41,    42,
      43,    44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,
      -1,    54,    -1,    56,    57,    58,    59,    60,    61,    62,
      63,    64,    65,    -1,    67,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    75,    -1,    -1,    -1,    -1,    80,    81,    -1,
      -1,    84,    85,    86,    -1,     5,    89,    90,     8,     9,
      10,    -1,    -1,    -1,    14,    15,    16,    17,    18,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    26,    27,    -1,    29,
      30,    31,    32,    33,    34,    35,    36,    37,    38,    39,
      40,    41,    42,    43,    44,    -1,    46,    47,    48,    -1,
      -1,    -1,    -1,    -1,    54,    -1,    56,    57,    58,    59,
      60,    61,    62,    63,    64,    65,    -1,    67,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,
      80,    81,    -1,    -1,    84,    85,    86,    -1,     5,    89,
      90,     8,     9,    10,    -1,    -1,    -1,    14,    15,    16,
      17,    18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,
      27,    -1,    29,    30,    31,    32,    33,    34,    35,    36,
      37,    38,    39,    40,    41,    42,    43,    44,    -1,    46,
      47,    48,    -1,    -1,    -1,    -1,    -1,    54,    -1,    56,
      57,    58,    59,    60,    61,    62,    63,    64,    65,    -1,
      67,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,
      -1,    -1,    -1,    80,    81,    -1,    -1,    84,    85,    86,
      -1,     5,    89,    90,     8,     9,    10,    -1,    -1,    -1,
      14,    15,    16,    17,    18,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    26,    27,    -1,    29,    30,    31,    32,    33,
      34,    35,    36,    37,    38,    39,    40,    41,    42,    43,
      44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,    -1,
      54,    -1,    56,    57,    58,    59,    60,    61,    62,    63,
      64,    65,    -1,    67,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    75,    -1,    -1,    -1,    -1,    80,    81,    -1,    -1,
      84,    85,    86,    -1,     5,    89,    90,     8,     9,    10,
      -1,    -1,    -1,    14,    15,    16,    17,    18,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    26,    27,    -1,    29,    30,
      31,    32,    33,    34,    35,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    -1,    46,    47,    48,    -1,    -1,
      -1,    -1,    -1,    54,    -1,    56,    57,    58,    59,    60,
      61,    62,    63,    64,    65,    -1,    67,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,    80,
      81,    -1,    -1,    84,    85,    86,    -1,     5,    89,    90,
       8,     9,    10,    -1,    -1,    -1,    14,    15,    16,    17,
      18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,    27,
      -1,    29,    30,    31,    32,    33,    34,    35,    36,    37,
      38,    39,    40,    41,    42,    43,    44,    -1,    46,    47,
      48,    -1,    -1,    -1,    -1,    -1,    54,    -1,    56,    57,
      58,    59,    60,    61,    62,    63,    64,    65,    -1,    67,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,
      -1,    -1,    80,    81,    -1,    -1,    84,    85,    86,    -1,
       5,    89,    90,     8,     9,    10,    -1,    -1,    -1,    14,
      15,    16,    17,    18,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    26,    27,    -1,    29,    30,    31,    32,    33,    34,
      35,    36,    37,    38,    39,    40,    41,    42,    43,    44,
      -1,    46,    47,    48,    -1,    -1,    -1,    -1,    -1,    54,
      -1,    56,    57,    58,    59,    60,    61,    62,    63,    64,
      65,    -1,    67,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      75,    -1,    -1,    -1,    -1,    80,    81,    -1,    -1,    84,
      85,    86,    -1,     5,    89,    90,     8,     9,    10,    -1,
      -1,    -1,    14,    15,    16,    17,    18,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    26,    27,    -1,    29,    30,    31,
      32,    33,    34,    35,    36,    37,    38,    39,    40,    41,
      42,    43,    44,    -1,    46,    47,    48,    -1,    -1,    -1,
      -1,    -1,    54,    -1,    56,    57,    58,    59,    60,    61,
      62,    63,    64,    65,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    75,    -1,    -1,    -1,    -1,    80,    81,
      -1,    -1,    84,    85,    86,    -1,     5,    89,    90,     8,
       9,    10,    -1,    -1,    -1,    14,    15,    16,    17,    18,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    26,    27,    -1,
      29,    30,    31,    32,    33,    34,    35,    36,    37,    38,
      39,    40,    41,    42,    43,    44,    -1,    46,    47,    48,
      -1,    -1,    -1,    -1,    -1,    54,    -1,    56,    57,    58,
      59,    60,    61,    62,    63,    64,    65,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    75,    -1,    -1,    -1,
      -1,    80,    81,    -1,    -1,    84,    85,    86,    -1,     5,
      89,    90,     8,     9,    10,    -1,    -1,    -1,    14,    15,
      16,    17,    18,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      26,    27,    -1,    29,    30,    31,    32,    33,    34,    35,
      36,    37,    38,    39,    40,    41,    42,    43,    44,    -1,
      46,    47,    48,    -1,    -1,    -1,    -1,    -1,    54,    -1,
      56,    57,    58,    59,    60,    61,    62,    63,    64,    65,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    75,
      -1,    -1,    -1,    -1,    80,    81,    -1,    -1,    84,    85,
      86,    -1,     5,    89,    90,     8,     9,    10,    -1,    -1,
      -1,    14,    15,    16,    17,    18,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    26,    27,    -1,    29,    30,    31,    32,
      33,    34,    35,    36,    37,    38,    39,    40,    41,    42,
      43,    44,    -1,    46,    47,    48,    -1,    -1,    -1,    -1,
      11,    54,    -1,    56,    57,    58,    59,    60,    61,    62,
      63,    64,    65,    -1,    -1,    -1,    -1,    49,    50,    51,
      52,    -1,    75,    -1,    -1,    -1,    -1,    80,    81,    -1,
      -1,    84,    85,    86,    -1,    11,    89,    90,    49,    50,
      51,    52,    74,    -1,    76,    77,    78,    79,    -1,    -1,
      -1,    83,    -1,    -1,    -1,    66,    67,    68,    -1,    70,
      71,    72,    73,    74,    -1,    76,    77,    78,    79,    -1,
      11,    -1,    83,    49,    50,    51,    52,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    11,    -1,    49,    50,    51,    52,
      66,    67,    68,    -1,    70,    71,    72,    73,    74,    -1,
      76,    77,    78,    79,    -1,    -1,    -1,    83,    49,    50,
      51,    52,    -1,    76,    77,    78,    79,    -1,    -1,    11,
      83,    -1,    49,    50,    51,    52,    67,    68,    69,    70,
      71,    72,    73,    74,    -1,    76,    77,    78,    79,    -1,
      67,    68,    83,    70,    71,    72,    73,    74,    -1,    76,
      77,    78,    79,    -1,    -1,    -1,    83,    49,    50,    51,
      52,    -1,    -1,    -1,    -1,    49,    50,    51,    52,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    68,    -1,    70,    71,
      72,    73,    74,    -1,    76,    77,    78,    79,    72,    73,
      74,    83,    76,    77,    78,    79,    -1,    -1,    -1,    83,
      49,    50,    51,    52,    -1,    -1,    -1,    -1,    49,    -1,
      51,    52,    -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1,
      -1,    -1,    -1,    -1,    -1,    -1,    -1,    76,    77,    78,
      79,    -1,    -1,    -1,    83,    76,    77,    78,    79,    -1,
      -1,    -1,    83
};

  /* YYSTOS[STATE-NUM] -- The (internal number of the) accessing
     symbol of state STATE-NUM.  */
static const yytype_uint8 yystos[] =
{
       0,    94,    95,     0,   100,     6,    13,    45,    53,   101,
     104,   107,   108,   109,   110,   111,     5,    67,     5,     5,
       1,     3,     5,     8,     9,    10,    14,    15,    16,    17,
      18,    19,    20,    21,    22,    26,    27,    28,    29,    30,
      31,    32,    33,    34,    35,    36,    37,    38,    39,    40,
      41,    42,    43,    44,    46,    47,    48,    54,    56,    57,
      58,    59,    60,    61,    62,    63,    64,    65,    75,    80,
      81,    84,    85,    86,    88,    89,    90,    96,    98,   102,
     103,   113,   115,   116,   117,   122,    67,    55,    98,    88,
      99,     5,    86,     5,    86,     5,     5,    56,    98,   115,
       5,    58,    86,   119,    86,    86,   119,   115,    86,    98,
      86,    98,    86,    98,    86,    98,    86,    86,    56,    86,
       5,    56,    86,    86,    86,    86,    86,    86,    86,    86,
      86,    86,    86,    86,    86,     5,    59,    86,   120,    86,
      86,     5,   115,    86,    56,    86,     5,    56,     3,    91,
       3,    91,     5,    56,    98,   113,    98,   115,   116,   116,
     116,   116,     4,   113,   116,   116,    23,    24,    25,    97,
      88,    19,    20,    21,    22,    66,    11,    49,    50,    51,
      52,    67,    68,    70,    71,    72,    73,    74,    76,    77,
      78,    79,    83,    84,    85,    55,   100,     5,   115,   118,
       5,   115,   118,    86,    86,   119,   119,   119,   106,   113,
      96,   113,    96,   113,    96,   113,    96,   115,   113,    86,
     102,   105,   113,     4,   118,   118,   118,   118,   118,   118,
       4,     4,   113,   115,   115,   115,   115,   115,   120,   120,
     115,   113,     3,    56,    86,    86,   113,   113,   113,   113,
     113,   113,   113,    91,     4,    66,   121,    98,    86,    98,
     113,   113,   113,   113,   115,   115,   115,   115,    67,   115,
      67,   115,   115,   115,   115,   115,    67,   115,    67,   115,
      67,   115,    67,   115,    67,   115,   115,   115,    67,   115,
      87,     4,    66,   112,    66,   114,     4,     4,   113,     4,
     113,     4,    66,     4,     4,     4,     4,     4,     4,   114,
     121,   113,    88,   121,     4,   112,   114,   114,    66,    66,
       4,   112,   114,   114,   114,   114,     4,   114,   112,   121,
     113,     3,     4,   113,     4,   113,    88,    92,    88,    92,
     113,     4,    91,   113,   115,   115,    69,   115,   115,   115,
     115,   115,   115,   113,     4,   115,   114,   121,   121,   113,
      96,    96,    96,    96,     4,   114,   121,   106,    96,     4,
     112,   114,   118,   118,     4,     4,   112,   112,   114,   114,
     112,     4,    88,   113,   121,   121,    87,    87,    92,   113,
       4,   115,   114,   121,     4,    96,    88,     4,   112,     4,
     114,     4,     4,   112,   114,     4,    87,    88,    92,    96,
       4,   105,     4,   114,     4,   112,    87,     4,   112,     4,
       4,    98,     4
};

  /* YYR1[YYN] -- Symbol number of symbol that rule YYN derives.  */
static const yytype_uint8 yyr1[] =
{
       0,    93,    95,    94,    96,    96,    97,    97,    97,    98,
      99,   100,   100,   101,   101,   101,   101,   101,   102,   102,
     102,   102,   102,   102,   103,   103,   103,   103,   104,   104,
     104,   104,   104,   104,   104,   104,   105,   105,   106,   106,
     107,   107,   108,   108,   108,   109,   109,   110,   111,   112,
     113,   113,   114,   115,   115,   115,   115,   115,   115,   115,
     115,   115,   115,   115,   115,   115,   115,   115,   115,   115,
     115,   115,   115,   115,   115,   115,   115,   115,   115,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   116,   116,   116,   116,   116,   116,   116,
     116,   116,   116,   117,   117,   117,   117,   117,   117,   118,
     118,   119,   119,   120,   120,   121,   121,   122
};

  /* YYR2[YYN] -- Number of symbols on the right hand side of rule YYN.  */
static const yytype_uint8 yyr2[] =
{
       0,     2,     0,     2,     3,     2,     0,     2,     5,     4,
       0,     0,     2,     1,     2,     1,     2,     3,     1,     1,
       3,     3,     3,     3,     5,     5,     3,     3,     6,     6,
       4,     4,     7,     6,    10,     2,     0,     1,     0,     1,
       0,     1,     1,     1,     1,     4,     3,     3,     3,     2,
       3,     1,     2,     3,     4,     4,     4,     4,     4,     4,
       4,     4,     3,     3,     3,     3,     3,     3,     3,     3,
       3,     3,     3,     3,     3,     5,     3,     3,     1,     2,
       2,     2,     2,     2,     2,     2,     2,     2,     2,     1,
       4,     3,     2,     2,     2,     1,     1,     4,     1,     1,
       5,     6,     5,     4,     5,     6,     8,     1,     1,     1,
       1,     1,     5,     5,     4,     4,     2,     5,     5,     4,
       4,     2,     1,     2,     1,     2,     2,     1,     2,     4,
       7,     2,     4,     5,     4,     2,     2,     3,     1,     5,
       6,     6,     7,     9,     6,     2,     4,     2,     4,     1,
       1,     6,     5,     4,     5,     4,     2,     1,     1,     3,
       3,     4,     5,     5,     6,     6,     7,     8,     4,     2,
       6,     1,     1,     1,     2,     2,     3,     3,     3,     1,
       1,     1,     1,     1,     1,     2,     1,     1
};


#define yyerrok         (yyerrstatus = 0)
#define yyclearin       (yychar = YYEMPTY)
#define YYEMPTY         (-2)
#define YYEOF           0

#define YYACCEPT        goto yyacceptlab
#define YYABORT         goto yyabortlab
#define YYERROR         goto yyerrorlab


#define YYRECOVERING()  (!!yyerrstatus)

#define YYBACKUP(Token, Value)                                  \
do                                                              \
  if (yychar == YYEMPTY)                                        \
    {                                                           \
      yychar = (Token);                                         \
      yylval = (Value);                                         \
      YYPOPSTACK (yylen);                                       \
      yystate = *yyssp;                                         \
      goto yybackup;                                            \
    }                                                           \
  else                                                          \
    {                                                           \
      yyerror (YY_("syntax error: cannot back up")); \
      YYERROR;                                                  \
    }                                                           \
while (0)

/* Error token number */
#define YYTERROR        1
#define YYERRCODE       256



/* Enable debugging if requested.  */
#if YYDEBUG

# ifndef YYFPRINTF
#  include <stdio.h> /* INFRINGES ON USER NAME SPACE */
#  define YYFPRINTF fprintf
# endif

# define YYDPRINTF(Args)                        \
do {                                            \
  if (yydebug)                                  \
    YYFPRINTF Args;                             \
} while (0)

/* This macro is provided for backward compatibility. */
#ifndef YY_LOCATION_PRINT
# define YY_LOCATION_PRINT(File, Loc) ((void) 0)
#endif


# define YY_SYMBOL_PRINT(Title, Type, Value, Location)                    \
do {                                                                      \
  if (yydebug)                                                            \
    {                                                                     \
      YYFPRINTF (stderr, "%s ", Title);                                   \
      yy_symbol_print (stderr,                                            \
                  Type, Value); \
      YYFPRINTF (stderr, "\n");                                           \
    }                                                                     \
} while (0)


/*----------------------------------------.
| Print this symbol's value on YYOUTPUT.  |
`----------------------------------------*/

static void
yy_symbol_value_print (FILE *yyoutput, int yytype, YYSTYPE const * const yyvaluep)
{
  FILE *yyo = yyoutput;
  YYUSE (yyo);
  if (!yyvaluep)
    return;
# ifdef YYPRINT
  if (yytype < YYNTOKENS)
    YYPRINT (yyoutput, yytoknum[yytype], *yyvaluep);
# endif
  YYUSE (yytype);
}


/*--------------------------------.
| Print this symbol on YYOUTPUT.  |
`--------------------------------*/

static void
yy_symbol_print (FILE *yyoutput, int yytype, YYSTYPE const * const yyvaluep)
{
  YYFPRINTF (yyoutput, "%s %s (",
             yytype < YYNTOKENS ? "token" : "nterm", yytname[yytype]);

  yy_symbol_value_print (yyoutput, yytype, yyvaluep);
  YYFPRINTF (yyoutput, ")");
}

/*------------------------------------------------------------------.
| yy_stack_print -- Print the state stack from its BOTTOM up to its |
| TOP (included).                                                   |
`------------------------------------------------------------------*/

static void
yy_stack_print (yytype_int16 *yybottom, yytype_int16 *yytop)
{
  YYFPRINTF (stderr, "Stack now");
  for (; yybottom <= yytop; yybottom++)
    {
      int yybot = *yybottom;
      YYFPRINTF (stderr, " %d", yybot);
    }
  YYFPRINTF (stderr, "\n");
}

# define YY_STACK_PRINT(Bottom, Top)                            \
do {                                                            \
  if (yydebug)                                                  \
    yy_stack_print ((Bottom), (Top));                           \
} while (0)


/*------------------------------------------------.
| Report that the YYRULE is going to be reduced.  |
`------------------------------------------------*/

static void
yy_reduce_print (yytype_int16 *yyssp, YYSTYPE *yyvsp, int yyrule)
{
  unsigned long int yylno = yyrline[yyrule];
  int yynrhs = yyr2[yyrule];
  int yyi;
  YYFPRINTF (stderr, "Reducing stack by rule %d (line %lu):\n",
             yyrule - 1, yylno);
  /* The symbols being reduced.  */
  for (yyi = 0; yyi < yynrhs; yyi++)
    {
      YYFPRINTF (stderr, "   $%d = ", yyi + 1);
      yy_symbol_print (stderr,
                       yystos[yyssp[yyi + 1 - yynrhs]],
                       &(yyvsp[(yyi + 1) - (yynrhs)])
                                              );
      YYFPRINTF (stderr, "\n");
    }
}

# define YY_REDUCE_PRINT(Rule)          \
do {                                    \
  if (yydebug)                          \
    yy_reduce_print (yyssp, yyvsp, Rule); \
} while (0)

/* Nonzero means print parse trace.  It is left uninitialized so that
   multiple parsers can coexist.  */
int yydebug;
#else /* !YYDEBUG */
# define YYDPRINTF(Args)
# define YY_SYMBOL_PRINT(Title, Type, Value, Location)
# define YY_STACK_PRINT(Bottom, Top)
# define YY_REDUCE_PRINT(Rule)
#endif /* !YYDEBUG */


/* YYINITDEPTH -- initial size of the parser's stacks.  */
#ifndef YYINITDEPTH
# define YYINITDEPTH 200
#endif

/* YYMAXDEPTH -- maximum size the stacks can grow to (effective only
   if the built-in stack extension method is used).

   Do not make this value too large; the results are undefined if
   YYSTACK_ALLOC_MAXIMUM < YYSTACK_BYTES (YYMAXDEPTH)
   evaluated with infinite-precision integer arithmetic.  */

#ifndef YYMAXDEPTH
# define YYMAXDEPTH 10000
#endif


#if YYERROR_VERBOSE

# ifndef yystrlen
#  if defined __GLIBC__ && defined _STRING_H
#   define yystrlen strlen
#  else
/* Return the length of YYSTR.  */
static YYSIZE_T
yystrlen (const char *yystr)
{
  YYSIZE_T yylen;
  for (yylen = 0; yystr[yylen]; yylen++)
    continue;
  return yylen;
}
#  endif
# endif

# ifndef yystpcpy
#  if defined __GLIBC__ && defined _STRING_H && defined _GNU_SOURCE
#   define yystpcpy stpcpy
#  else
/* Copy YYSRC to YYDEST, returning the address of the terminating '\0' in
   YYDEST.  */
static char *
yystpcpy (char *yydest, const char *yysrc)
{
  char *yyd = yydest;
  const char *yys = yysrc;

  while ((*yyd++ = *yys++) != '\0')
    continue;

  return yyd - 1;
}
#  endif
# endif

# ifndef yytnamerr
/* Copy to YYRES the contents of YYSTR after stripping away unnecessary
   quotes and backslashes, so that it's suitable for yyerror.  The
   heuristic is that double-quoting is unnecessary unless the string
   contains an apostrophe, a comma, or backslash (other than
   backslash-backslash).  YYSTR is taken from yytname.  If YYRES is
   null, do not copy; instead, return the length of what the result
   would have been.  */
static YYSIZE_T
yytnamerr (char *yyres, const char *yystr)
{
  if (*yystr == '"')
    {
      YYSIZE_T yyn = 0;
      char const *yyp = yystr;

      for (;;)
        switch (*++yyp)
          {
          case '\'':
          case ',':
            goto do_not_strip_quotes;

          case '\\':
            if (*++yyp != '\\')
              goto do_not_strip_quotes;
            /* Fall through.  */
          default:
            if (yyres)
              yyres[yyn] = *yyp;
            yyn++;
            break;

          case '"':
            if (yyres)
              yyres[yyn] = '\0';
            return yyn;
          }
    do_not_strip_quotes: ;
    }

  if (! yyres)
    return yystrlen (yystr);

  return yystpcpy (yyres, yystr) - yyres;
}
# endif

/* Copy into *YYMSG, which is of size *YYMSG_ALLOC, an error message
   about the unexpected token YYTOKEN for the state stack whose top is
   YYSSP.

   Return 0 if *YYMSG was successfully written.  Return 1 if *YYMSG is
   not large enough to hold the message.  In that case, also set
   *YYMSG_ALLOC to the required number of bytes.  Return 2 if the
   required number of bytes is too large to store.  */
static int
yysyntax_error (YYSIZE_T *yymsg_alloc, char **yymsg,
                yytype_int16 *yyssp, int yytoken)
{
  YYSIZE_T yysize0 = yytnamerr (YY_NULLPTR, yytname[yytoken]);
  YYSIZE_T yysize = yysize0;
  enum { YYERROR_VERBOSE_ARGS_MAXIMUM = 5 };
  /* Internationalized format string. */
  const char *yyformat = YY_NULLPTR;
  /* Arguments of yyformat. */
  char const *yyarg[YYERROR_VERBOSE_ARGS_MAXIMUM];
  /* Number of reported tokens (one for the "unexpected", one per
     "expected"). */
  int yycount = 0;

  /* There are many possibilities here to consider:
     - If this state is a consistent state with a default action, then
       the only way this function was invoked is if the default action
       is an error action.  In that case, don't check for expected
       tokens because there are none.
     - The only way there can be no lookahead present (in yychar) is if
       this state is a consistent state with a default action.  Thus,
       detecting the absence of a lookahead is sufficient to determine
       that there is no unexpected or expected token to report.  In that
       case, just report a simple "syntax error".
     - Don't assume there isn't a lookahead just because this state is a
       consistent state with a default action.  There might have been a
       previous inconsistent state, consistent state with a non-default
       action, or user semantic action that manipulated yychar.
     - Of course, the expected token list depends on states to have
       correct lookahead information, and it depends on the parser not
       to perform extra reductions after fetching a lookahead from the
       scanner and before detecting a syntax error.  Thus, state merging
       (from LALR or IELR) and default reductions corrupt the expected
       token list.  However, the list is correct for canonical LR with
       one exception: it will still contain any token that will not be
       accepted due to an error action in a later state.
  */
  if (yytoken != YYEMPTY)
    {
      int yyn = yypact[*yyssp];
      yyarg[yycount++] = yytname[yytoken];
      if (!yypact_value_is_default (yyn))
        {
          /* Start YYX at -YYN if negative to avoid negative indexes in
             YYCHECK.  In other words, skip the first -YYN actions for
             this state because they are default actions.  */
          int yyxbegin = yyn < 0 ? -yyn : 0;
          /* Stay within bounds of both yycheck and yytname.  */
          int yychecklim = YYLAST - yyn + 1;
          int yyxend = yychecklim < YYNTOKENS ? yychecklim : YYNTOKENS;
          int yyx;

          for (yyx = yyxbegin; yyx < yyxend; ++yyx)
            if (yycheck[yyx + yyn] == yyx && yyx != YYTERROR
                && !yytable_value_is_error (yytable[yyx + yyn]))
              {
                if (yycount == YYERROR_VERBOSE_ARGS_MAXIMUM)
                  {
                    yycount = 1;
                    yysize = yysize0;
                    break;
                  }
                yyarg[yycount++] = yytname[yyx];
                {
                  YYSIZE_T yysize1 = yysize + yytnamerr (YY_NULLPTR, yytname[yyx]);
                  if (! (yysize <= yysize1
                         && yysize1 <= YYSTACK_ALLOC_MAXIMUM))
                    return 2;
                  yysize = yysize1;
                }
              }
        }
    }

  switch (yycount)
    {
# define YYCASE_(N, S)                      \
      case N:                               \
        yyformat = S;                       \
      break
      YYCASE_(0, YY_("syntax error"));
      YYCASE_(1, YY_("syntax error, unexpected %s"));
      YYCASE_(2, YY_("syntax error, unexpected %s, expecting %s"));
      YYCASE_(3, YY_("syntax error, unexpected %s, expecting %s or %s"));
      YYCASE_(4, YY_("syntax error, unexpected %s, expecting %s or %s or %s"));
      YYCASE_(5, YY_("syntax error, unexpected %s, expecting %s or %s or %s or %s"));
# undef YYCASE_
    }

  {
    YYSIZE_T yysize1 = yysize + yystrlen (yyformat);
    if (! (yysize <= yysize1 && yysize1 <= YYSTACK_ALLOC_MAXIMUM))
      return 2;
    yysize = yysize1;
  }

  if (*yymsg_alloc < yysize)
    {
      *yymsg_alloc = 2 * yysize;
      if (! (yysize <= *yymsg_alloc
             && *yymsg_alloc <= YYSTACK_ALLOC_MAXIMUM))
        *yymsg_alloc = YYSTACK_ALLOC_MAXIMUM;
      return 1;
    }

  /* Avoid sprintf, as that infringes on the user's name space.
     Don't have undefined behavior even if the translation
     produced a string with the wrong number of "%s"s.  */
  {
    char *yyp = *yymsg;
    int yyi = 0;
    while ((*yyp = *yyformat) != '\0')
      if (*yyp == '%' && yyformat[1] == 's' && yyi < yycount)
        {
          yyp += yytnamerr (yyp, yyarg[yyi++]);
          yyformat += 2;
        }
      else
        {
          yyp++;
          yyformat++;
        }
  }
  return 0;
}
#endif /* YYERROR_VERBOSE */

/*-----------------------------------------------.
| Release the memory associated to this symbol.  |
`-----------------------------------------------*/

static void
yydestruct (const char *yymsg, int yytype, YYSTYPE *yyvaluep)
{
  YYUSE (yyvaluep);
  if (!yymsg)
    yymsg = "Deleting";
  YY_SYMBOL_PRINT (yymsg, yytype, yyvaluep, yylocationp);

  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  YYUSE (yytype);
  YY_IGNORE_MAYBE_UNINITIALIZED_END
}




/* The lookahead symbol.  */
int yychar;

/* The semantic value of the lookahead symbol.  */
YYSTYPE yylval;
/* Number of syntax errors so far.  */
int yynerrs;


/*----------.
| yyparse.  |
`----------*/

int
yyparse (void)
{
    int yystate;
    /* Number of tokens to shift before error messages enabled.  */
    int yyerrstatus;

    /* The stacks and their tools:
       'yyss': related to states.
       'yyvs': related to semantic values.

       Refer to the stacks through separate pointers, to allow yyoverflow
       to reallocate them elsewhere.  */

    /* The state stack.  */
    yytype_int16 yyssa[YYINITDEPTH];
    yytype_int16 *yyss;
    yytype_int16 *yyssp;

    /* The semantic value stack.  */
    YYSTYPE yyvsa[YYINITDEPTH];
    YYSTYPE *yyvs;
    YYSTYPE *yyvsp;

    YYSIZE_T yystacksize;

  int yyn;
  int yyresult;
  /* Lookahead token as an internal (translated) token number.  */
  int yytoken = 0;
  /* The variables used to return semantic value and location from the
     action routines.  */
  YYSTYPE yyval;

#if YYERROR_VERBOSE
  /* Buffer for error messages, and its allocated size.  */
  char yymsgbuf[128];
  char *yymsg = yymsgbuf;
  YYSIZE_T yymsg_alloc = sizeof yymsgbuf;
#endif

#define YYPOPSTACK(N)   (yyvsp -= (N), yyssp -= (N))

  /* The number of symbols on the RHS of the reduced rule.
     Keep to zero when no symbol should be popped.  */
  int yylen = 0;

  yyssp = yyss = yyssa;
  yyvsp = yyvs = yyvsa;
  yystacksize = YYINITDEPTH;

  YYDPRINTF ((stderr, "Starting parse\n"));

  yystate = 0;
  yyerrstatus = 0;
  yynerrs = 0;
  yychar = YYEMPTY; /* Cause a token to be read.  */
  goto yysetstate;

/*------------------------------------------------------------.
| yynewstate -- Push a new state, which is found in yystate.  |
`------------------------------------------------------------*/
 yynewstate:
  /* In all cases, when you get here, the value and location stacks
     have just been pushed.  So pushing a state here evens the stacks.  */
  yyssp++;

 yysetstate:
  *yyssp = yystate;

  if (yyss + yystacksize - 1 <= yyssp)
    {
      /* Get the current used size of the three stacks, in elements.  */
      YYSIZE_T yysize = yyssp - yyss + 1;

#ifdef yyoverflow
      {
        /* Give user a chance to reallocate the stack.  Use copies of
           these so that the &'s don't force the real ones into
           memory.  */
        YYSTYPE *yyvs1 = yyvs;
        yytype_int16 *yyss1 = yyss;

        /* Each stack pointer address is followed by the size of the
           data in use in that stack, in bytes.  This used to be a
           conditional around just the two extra args, but that might
           be undefined if yyoverflow is a macro.  */
        yyoverflow (YY_("memory exhausted"),
                    &yyss1, yysize * sizeof (*yyssp),
                    &yyvs1, yysize * sizeof (*yyvsp),
                    &yystacksize);

        yyss = yyss1;
        yyvs = yyvs1;
      }
#else /* no yyoverflow */
# ifndef YYSTACK_RELOCATE
      goto yyexhaustedlab;
# else
      /* Extend the stack our own way.  */
      if (YYMAXDEPTH <= yystacksize)
        goto yyexhaustedlab;
      yystacksize *= 2;
      if (YYMAXDEPTH < yystacksize)
        yystacksize = YYMAXDEPTH;

      {
        yytype_int16 *yyss1 = yyss;
        union yyalloc *yyptr =
          (union yyalloc *) YYSTACK_ALLOC (YYSTACK_BYTES (yystacksize));
        if (! yyptr)
          goto yyexhaustedlab;
        YYSTACK_RELOCATE (yyss_alloc, yyss);
        YYSTACK_RELOCATE (yyvs_alloc, yyvs);
#  undef YYSTACK_RELOCATE
        if (yyss1 != yyssa)
          YYSTACK_FREE (yyss1);
      }
# endif
#endif /* no yyoverflow */

      yyssp = yyss + yysize - 1;
      yyvsp = yyvs + yysize - 1;

      YYDPRINTF ((stderr, "Stack size increased to %lu\n",
                  (unsigned long int) yystacksize));

      if (yyss + yystacksize - 1 <= yyssp)
        YYABORT;
    }

  YYDPRINTF ((stderr, "Entering state %d\n", yystate));

  if (yystate == YYFINAL)
    YYACCEPT;

  goto yybackup;

/*-----------.
| yybackup.  |
`-----------*/
yybackup:

  /* Do appropriate processing given the current state.  Read a
     lookahead token if we need one and don't already have one.  */

  /* First try to decide what to do without reference to lookahead token.  */
  yyn = yypact[yystate];
  if (yypact_value_is_default (yyn))
    goto yydefault;

  /* Not known => get a lookahead token if don't already have one.  */

  /* YYCHAR is either YYEMPTY or YYEOF or a valid lookahead symbol.  */
  if (yychar == YYEMPTY)
    {
      YYDPRINTF ((stderr, "Reading a token: "));
      yychar = yylex ();
    }

  if (yychar <= YYEOF)
    {
      yychar = yytoken = YYEOF;
      YYDPRINTF ((stderr, "Now at end of input.\n"));
    }
  else
    {
      yytoken = YYTRANSLATE (yychar);
      YY_SYMBOL_PRINT ("Next token is", yytoken, &yylval, &yylloc);
    }

  /* If the proper action on seeing token YYTOKEN is to reduce or to
     detect an error, take that action.  */
  yyn += yytoken;
  if (yyn < 0 || YYLAST < yyn || yycheck[yyn] != yytoken)
    goto yydefault;
  yyn = yytable[yyn];
  if (yyn <= 0)
    {
      if (yytable_value_is_error (yyn))
        goto yyerrlab;
      yyn = -yyn;
      goto yyreduce;
    }

  /* Count tokens shifted since error; after three, turn off error
     status.  */
  if (yyerrstatus)
    yyerrstatus--;

  /* Shift the lookahead token.  */
  YY_SYMBOL_PRINT ("Shifting", yytoken, &yylval, &yylloc);

  /* Discard the shifted token.  */
  yychar = YYEMPTY;

  yystate = yyn;
  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  *++yyvsp = yylval;
  YY_IGNORE_MAYBE_UNINITIALIZED_END

  goto yynewstate;


/*-----------------------------------------------------------.
| yydefault -- do the default action for the current state.  |
`-----------------------------------------------------------*/
yydefault:
  yyn = yydefact[yystate];
  if (yyn == 0)
    goto yyerrlab;
  goto yyreduce;


/*-----------------------------.
| yyreduce -- Do a reduction.  |
`-----------------------------*/
yyreduce:
  /* yyn is the number of a rule to reduce with.  */
  yylen = yyr2[yyn];

  /* If YYLEN is nonzero, implement the default value of the action:
     '$$ = $1'.

     Otherwise, the following line sets YYVAL to garbage.
     This behavior is undocumented and Bison
     users should not rely upon it.  Assigning to YYVAL
     unconditionally makes the parser a bit smaller, and it avoids a
     GCC warning that YYVAL may be used uninitialized.  */
  yyval = yyvsp[1-yylen];


  YY_REDUCE_PRINT (yyn);
  switch (yyn)
    {
        case 2:
#line 112 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    {
#if defined(YYDEBUG) && defined(DEBUGGING)
		    yydebug = (debug & 1);
#endif
		    expectterm = 2;
		}
#line 2163 "perly.c" /* yacc.c:1646  */
    break;

  case 3:
#line 119 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { if (in_eval)
				eval_root = block_head((yyvsp[0].cmdval));
			    else
				main_root = block_head((yyvsp[0].cmdval)); }
#line 2172 "perly.c" /* yacc.c:1646  */
    break;

  case 4:
#line 126 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.compval).comp_true = (yyvsp[-2].cmdval); (yyval.compval).comp_alt = (yyvsp[0].cmdval); }
#line 2178 "perly.c" /* yacc.c:1646  */
    break;

  case 5:
#line 128 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.compval).comp_true = (yyvsp[-1].cmdval); (yyval.compval).comp_alt = (yyvsp[0].cmdval); }
#line 2184 "perly.c" /* yacc.c:1646  */
    break;

  case 6:
#line 132 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = Nullcmd; }
#line 2190 "perly.c" /* yacc.c:1646  */
    break;

  case 7:
#line 134 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = (yyvsp[0].cmdval); }
#line 2196 "perly.c" /* yacc.c:1646  */
    break;

  case 8:
#line 136 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-4].ival);
			    (yyval.cmdval) = make_ccmd(C_ELSIF,1,(yyvsp[-2].arg),(yyvsp[0].compval)); }
#line 2203 "perly.c" /* yacc.c:1646  */
    break;

  case 9:
#line 141 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = block_head((yyvsp[-1].cmdval));
			  if (cmdline > (line_t)(yyvsp[-3].ival))
			      cmdline = (yyvsp[-3].ival);
			  if (savestack->ary_fill > (yyvsp[-2].ival))
			    restorelist((yyvsp[-2].ival));
			  expectterm = 2; }
#line 2214 "perly.c" /* yacc.c:1646  */
    break;

  case 10:
#line 150 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.ival) = savestack->ary_fill; }
#line 2220 "perly.c" /* yacc.c:1646  */
    break;

  case 11:
#line 154 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = Nullcmd; }
#line 2226 "perly.c" /* yacc.c:1646  */
    break;

  case 12:
#line 156 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = append_line((yyvsp[-1].cmdval),(yyvsp[0].cmdval)); }
#line 2232 "perly.c" /* yacc.c:1646  */
    break;

  case 13:
#line 160 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = Nullcmd; }
#line 2238 "perly.c" /* yacc.c:1646  */
    break;

  case 14:
#line 162 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = add_label((yyvsp[-1].cval),(yyvsp[0].cmdval)); }
#line 2244 "perly.c" /* yacc.c:1646  */
    break;

  case 16:
#line 165 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { if ((yyvsp[-1].cval) != Nullch) {
			      (yyval.cmdval) = add_label((yyvsp[-1].cval), make_acmd(C_EXPR, Nullstab,
				  Nullarg, Nullarg) );
			    }
			    else {
			      (yyval.cmdval) = Nullcmd;
			      cmdline = NOLINE;
			    }
			    expectterm = 2; }
#line 2258 "perly.c" /* yacc.c:1646  */
    break;

  case 17:
#line 175 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = add_label((yyvsp[-2].cval),(yyvsp[-1].cmdval));
			  expectterm = 2; }
#line 2265 "perly.c" /* yacc.c:1646  */
    break;

  case 18:
#line 180 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = Nullcmd; }
#line 2271 "perly.c" /* yacc.c:1646  */
    break;

  case 19:
#line 182 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = make_acmd(C_EXPR, Nullstab, (yyvsp[0].arg), Nullarg); }
#line 2277 "perly.c" /* yacc.c:1646  */
    break;

  case 20:
#line 184 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = addcond(
			       make_acmd(C_EXPR, Nullstab, Nullarg, (yyvsp[-2].arg)), (yyvsp[0].arg)); }
#line 2284 "perly.c" /* yacc.c:1646  */
    break;

  case 21:
#line 187 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = addcond(invert(
			       make_acmd(C_EXPR, Nullstab, Nullarg, (yyvsp[-2].arg))), (yyvsp[0].arg)); }
#line 2291 "perly.c" /* yacc.c:1646  */
    break;

  case 22:
#line 190 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = addloop(
			       make_acmd(C_EXPR, Nullstab, Nullarg, (yyvsp[-2].arg)), (yyvsp[0].arg)); }
#line 2298 "perly.c" /* yacc.c:1646  */
    break;

  case 23:
#line 193 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = addloop(invert(
			       make_acmd(C_EXPR, Nullstab, Nullarg, (yyvsp[-2].arg))), (yyvsp[0].arg)); }
#line 2305 "perly.c" /* yacc.c:1646  */
    break;

  case 24:
#line 198 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-4].ival);
			    (yyval.cmdval) = make_icmd(C_IF,(yyvsp[-2].arg),(yyvsp[0].compval)); }
#line 2312 "perly.c" /* yacc.c:1646  */
    break;

  case 25:
#line 201 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-4].ival);
			    (yyval.cmdval) = invert(make_icmd(C_IF,(yyvsp[-2].arg),(yyvsp[0].compval))); }
#line 2319 "perly.c" /* yacc.c:1646  */
    break;

  case 26:
#line 204 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-2].ival);
			    (yyval.cmdval) = make_icmd(C_IF,cmd_to_arg((yyvsp[-1].cmdval)),(yyvsp[0].compval)); }
#line 2326 "perly.c" /* yacc.c:1646  */
    break;

  case 27:
#line 207 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-2].ival);
			    (yyval.cmdval) = invert(make_icmd(C_IF,cmd_to_arg((yyvsp[-1].cmdval)),(yyvsp[0].compval))); }
#line 2333 "perly.c" /* yacc.c:1646  */
    break;

  case 28:
#line 212 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-4].ival);
			    (yyval.cmdval) = wopt(add_label((yyvsp[-5].cval),
			    make_ccmd(C_WHILE,1,(yyvsp[-2].arg),(yyvsp[0].compval)) )); }
#line 2341 "perly.c" /* yacc.c:1646  */
    break;

  case 29:
#line 216 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-4].ival);
			    (yyval.cmdval) = wopt(add_label((yyvsp[-5].cval),
			    invert(make_ccmd(C_WHILE,1,(yyvsp[-2].arg),(yyvsp[0].compval))) )); }
#line 2349 "perly.c" /* yacc.c:1646  */
    break;

  case 30:
#line 220 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-2].ival);
			    (yyval.cmdval) = wopt(add_label((yyvsp[-3].cval),
			    make_ccmd(C_WHILE, 1, cmd_to_arg((yyvsp[-1].cmdval)),(yyvsp[0].compval)) )); }
#line 2357 "perly.c" /* yacc.c:1646  */
    break;

  case 31:
#line 224 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-2].ival);
			    (yyval.cmdval) = wopt(add_label((yyvsp[-3].cval),
			    invert(make_ccmd(C_WHILE,1,cmd_to_arg((yyvsp[-1].cmdval)),(yyvsp[0].compval))) )); }
#line 2365 "perly.c" /* yacc.c:1646  */
    break;

  case 32:
#line 228 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-5].ival);
			    /*
			     * The following gobbledygook catches EXPRs that
			     * aren't explicit array refs and translates
			     *		foreach VAR (EXPR) {
			     * into
			     *		@ary = EXPR;
			     *		foreach VAR (@ary) {
			     * where @ary is a hidden array made by genstab().
			     * (Note that @ary may become a local array if
			     * it is determined that it might be called
			     * recursively.  See cmd_tosave().)
			     */
			    if ((yyvsp[-2].arg)->arg_type != O_ARRAY) {
				scrstab = aadd(genstab());
				(yyval.cmdval) = append_line(
				    make_acmd(C_EXPR, Nullstab,
				      l(make_op(O_ASSIGN,2,
					listish(make_op(O_ARRAY, 1,
					  stab2arg(A_STAB,scrstab),
					  Nullarg,Nullarg )),
					listish(make_list((yyvsp[-2].arg))),
					Nullarg)),
				      Nullarg),
				    wopt(over((yyvsp[-4].stabval),add_label((yyvsp[-6].cval),
				      make_ccmd(C_WHILE, 0,
					make_op(O_ARRAY, 1,
					  stab2arg(A_STAB,scrstab),
					  Nullarg,Nullarg ),
					(yyvsp[0].compval))))));
				(yyval.cmdval)->c_line = (yyvsp[-5].ival);
				(yyval.cmdval)->c_head->c_line = (yyvsp[-5].ival);
			    }
			    else {
				(yyval.cmdval) = wopt(over((yyvsp[-4].stabval),add_label((yyvsp[-6].cval),
				make_ccmd(C_WHILE,1,(yyvsp[-2].arg),(yyvsp[0].compval)) )));
			    }
			}
#line 2408 "perly.c" /* yacc.c:1646  */
    break;

  case 33:
#line 267 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { cmdline = (yyvsp[-4].ival);
			    if ((yyvsp[-2].arg)->arg_type != O_ARRAY) {
				scrstab = aadd(genstab());
				(yyval.cmdval) = append_line(
				    make_acmd(C_EXPR, Nullstab,
				      l(make_op(O_ASSIGN,2,
					listish(make_op(O_ARRAY, 1,
					  stab2arg(A_STAB,scrstab),
					  Nullarg,Nullarg )),
					listish(make_list((yyvsp[-2].arg))),
					Nullarg)),
				      Nullarg),
				    wopt(over(defstab,add_label((yyvsp[-5].cval),
				      make_ccmd(C_WHILE, 0,
					make_op(O_ARRAY, 1,
					  stab2arg(A_STAB,scrstab),
					  Nullarg,Nullarg ),
					(yyvsp[0].compval))))));
				(yyval.cmdval)->c_line = (yyvsp[-4].ival);
				(yyval.cmdval)->c_head->c_line = (yyvsp[-4].ival);
			    }
			    else {	/* lisp, anyone? */
				(yyval.cmdval) = wopt(over(defstab,add_label((yyvsp[-5].cval),
				make_ccmd(C_WHILE,1,(yyvsp[-2].arg),(yyvsp[0].compval)) )));
			    }
			}
#line 2439 "perly.c" /* yacc.c:1646  */
    break;

  case 34:
#line 295 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    {   yyval.compval.comp_true = (yyvsp[0].cmdval);
			    yyval.compval.comp_alt = (yyvsp[-2].cmdval);
			    cmdline = (yyvsp[-8].ival);
			    (yyval.cmdval) = append_line((yyvsp[-6].cmdval),wopt(add_label((yyvsp[-9].cval),
				make_ccmd(C_WHILE,1,(yyvsp[-4].arg),yyval.compval) ))); }
#line 2449 "perly.c" /* yacc.c:1646  */
    break;

  case 35:
#line 301 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = add_label((yyvsp[-1].cval),make_ccmd(C_BLOCK,1,Nullarg,(yyvsp[0].compval))); }
#line 2455 "perly.c" /* yacc.c:1646  */
    break;

  case 36:
#line 305 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cmdval) = Nullcmd; }
#line 2461 "perly.c" /* yacc.c:1646  */
    break;

  case 38:
#line 310 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (void)scanstr("1",SCAN_DEF); (yyval.arg) = yylval.arg; }
#line 2467 "perly.c" /* yacc.c:1646  */
    break;

  case 40:
#line 315 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.cval) = Nullch; }
#line 2473 "perly.c" /* yacc.c:1646  */
    break;

  case 42:
#line 320 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.ival) = 0; }
#line 2479 "perly.c" /* yacc.c:1646  */
    break;

  case 43:
#line 322 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.ival) = 0; }
#line 2485 "perly.c" /* yacc.c:1646  */
    break;

  case 44:
#line 324 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.ival) = 0; }
#line 2491 "perly.c" /* yacc.c:1646  */
    break;

  case 45:
#line 328 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { if (strEQ((yyvsp[-2].cval),"stdout"))
			    make_form(stabent("STDOUT",TRUE),(yyvsp[0].formval));
			  else if (strEQ((yyvsp[-2].cval),"stderr"))
			    make_form(stabent("STDERR",TRUE),(yyvsp[0].formval));
			  else
			    make_form(stabent((yyvsp[-2].cval),TRUE),(yyvsp[0].formval));
			  Safefree((yyvsp[-2].cval)); (yyvsp[-2].cval) = Nullch; }
#line 2503 "perly.c" /* yacc.c:1646  */
    break;

  case 46:
#line 336 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { make_form(stabent("STDOUT",TRUE),(yyvsp[0].formval)); }
#line 2509 "perly.c" /* yacc.c:1646  */
    break;

  case 47:
#line 340 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { make_sub((yyvsp[-1].cval),(yyvsp[0].cmdval));
			  cmdline = NOLINE;
			  if (savestack->ary_fill > (yyvsp[-2].ival))
			    restorelist((yyvsp[-2].ival)); }
#line 2518 "perly.c" /* yacc.c:1646  */
    break;

  case 48:
#line 347 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { char tmpbuf[256];
			  STAB *tmpstab;

			  savehptr(&curstash);
			  saveitem(curstname);
			  str_set(curstname,(yyvsp[-1].cval));
			  sprintf(tmpbuf,"'_%s",(yyvsp[-1].cval));
			  tmpstab = stabent(tmpbuf,TRUE);
			  if (!stab_xhash(tmpstab))
			      stab_xhash(tmpstab) = hnew(0);
			  curstash = stab_xhash(tmpstab);
			  if (!curstash->tbl_name)
			      curstash->tbl_name = savestr((yyvsp[-1].cval));
			  curstash->tbl_coeffsize = 0;
			  Safefree((yyvsp[-1].cval)); (yyvsp[-1].cval) = Nullch;
			  cmdline = NOLINE;
			  expectterm = 2;
			}
#line 2541 "perly.c" /* yacc.c:1646  */
    break;

  case 49:
#line 368 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2547 "perly.c" /* yacc.c:1646  */
    break;

  case 50:
#line 372 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_COMMA, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2553 "perly.c" /* yacc.c:1646  */
    break;

  case 52:
#line 377 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2559 "perly.c" /* yacc.c:1646  */
    break;

  case 53:
#line 381 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    {   (yyvsp[-2].arg) = listish((yyvsp[-2].arg));
			    if ((yyvsp[-2].arg)->arg_type == O_ASSIGN && (yyvsp[-2].arg)->arg_len == 1)
				(yyvsp[-2].arg)->arg_type = O_ITEM;	/* a local() */
			    if ((yyvsp[-2].arg)->arg_type == O_LIST)
				(yyvsp[0].arg) = listish((yyvsp[0].arg));
			    (yyval.arg) = l(make_op(O_ASSIGN, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg)); }
#line 2570 "perly.c" /* yacc.c:1646  */
    break;

  case 54:
#line 388 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op(O_POW, 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2576 "perly.c" /* yacc.c:1646  */
    break;

  case 55:
#line 390 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op((yyvsp[-2].ival), 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2582 "perly.c" /* yacc.c:1646  */
    break;

  case 56:
#line 392 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = rcatmaybe(l(make_op((yyvsp[-2].ival), 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)));}
#line 2588 "perly.c" /* yacc.c:1646  */
    break;

  case 57:
#line 394 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op(O_LEFT_SHIFT, 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2594 "perly.c" /* yacc.c:1646  */
    break;

  case 58:
#line 396 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op(O_RIGHT_SHIFT, 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2600 "perly.c" /* yacc.c:1646  */
    break;

  case 59:
#line 398 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op(O_BIT_AND, 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2606 "perly.c" /* yacc.c:1646  */
    break;

  case 60:
#line 400 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op(O_XOR, 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2612 "perly.c" /* yacc.c:1646  */
    break;

  case 61:
#line 402 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op(O_BIT_OR, 2, (yyvsp[-3].arg), (yyvsp[0].arg), Nullarg)); }
#line 2618 "perly.c" /* yacc.c:1646  */
    break;

  case 62:
#line 406 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_POW, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2624 "perly.c" /* yacc.c:1646  */
    break;

  case 63:
#line 408 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { if ((yyvsp[-1].ival) == O_REPEAT)
			      (yyvsp[-2].arg) = listish((yyvsp[-2].arg));
			    (yyval.arg) = make_op((yyvsp[-1].ival), 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg);
			    if ((yyvsp[-1].ival) == O_REPEAT) {
				if ((yyval.arg)[1].arg_type != A_EXPR ||
				  (yyval.arg)[1].arg_ptr.arg_arg->arg_type != O_LIST)
				    (yyval.arg)[1].arg_flags &= ~AF_ARYOK;
			    } }
#line 2637 "perly.c" /* yacc.c:1646  */
    break;

  case 64:
#line 417 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival), 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2643 "perly.c" /* yacc.c:1646  */
    break;

  case 65:
#line 419 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_LEFT_SHIFT, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2649 "perly.c" /* yacc.c:1646  */
    break;

  case 66:
#line 421 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_RIGHT_SHIFT, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2655 "perly.c" /* yacc.c:1646  */
    break;

  case 67:
#line 423 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival), 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2661 "perly.c" /* yacc.c:1646  */
    break;

  case 68:
#line 425 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival), 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2667 "perly.c" /* yacc.c:1646  */
    break;

  case 69:
#line 427 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_BIT_AND, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2673 "perly.c" /* yacc.c:1646  */
    break;

  case 70:
#line 429 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_XOR, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2679 "perly.c" /* yacc.c:1646  */
    break;

  case 71:
#line 431 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_BIT_OR, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2685 "perly.c" /* yacc.c:1646  */
    break;

  case 72:
#line 433 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { arg4 = Nullarg;
			  (yyval.arg) = make_op(O_F_OR_R, 4, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg);
			  (yyval.arg)[0].arg_flags |= (yyvsp[-1].ival); }
#line 2693 "perly.c" /* yacc.c:1646  */
    break;

  case 73:
#line 437 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_AND, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2699 "perly.c" /* yacc.c:1646  */
    break;

  case 74:
#line 439 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_OR, 2, (yyvsp[-2].arg), (yyvsp[0].arg), Nullarg); }
#line 2705 "perly.c" /* yacc.c:1646  */
    break;

  case 75:
#line 441 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_COND_EXPR, 3, (yyvsp[-4].arg), (yyvsp[-2].arg), (yyvsp[0].arg)); }
#line 2711 "perly.c" /* yacc.c:1646  */
    break;

  case 76:
#line 443 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = mod_match(O_MATCH, (yyvsp[-2].arg), (yyvsp[0].arg)); }
#line 2717 "perly.c" /* yacc.c:1646  */
    break;

  case 77:
#line 445 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = mod_match(O_NMATCH, (yyvsp[-2].arg), (yyvsp[0].arg)); }
#line 2723 "perly.c" /* yacc.c:1646  */
    break;

  case 78:
#line 447 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2729 "perly.c" /* yacc.c:1646  */
    break;

  case 79:
#line 451 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_NEGATE, 1, (yyvsp[0].arg), Nullarg, Nullarg); }
#line 2735 "perly.c" /* yacc.c:1646  */
    break;

  case 80:
#line 453 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2741 "perly.c" /* yacc.c:1646  */
    break;

  case 81:
#line 455 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_NOT, 1, (yyvsp[0].arg), Nullarg, Nullarg); }
#line 2747 "perly.c" /* yacc.c:1646  */
    break;

  case 82:
#line 457 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_COMPLEMENT, 1, (yyvsp[0].arg), Nullarg, Nullarg);}
#line 2753 "perly.c" /* yacc.c:1646  */
    break;

  case 83:
#line 459 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = addflags(1, AF_POST|AF_UP,
			    l(make_op(O_ITEM,1,(yyvsp[-1].arg),Nullarg,Nullarg))); }
#line 2760 "perly.c" /* yacc.c:1646  */
    break;

  case 84:
#line 462 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = addflags(1, AF_POST,
			    l(make_op(O_ITEM,1,(yyvsp[-1].arg),Nullarg,Nullarg))); }
#line 2767 "perly.c" /* yacc.c:1646  */
    break;

  case 85:
#line 465 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = addflags(1, AF_PRE|AF_UP,
			    l(make_op(O_ITEM,1,(yyvsp[0].arg),Nullarg,Nullarg))); }
#line 2774 "perly.c" /* yacc.c:1646  */
    break;

  case 86:
#line 468 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = addflags(1, AF_PRE,
			    l(make_op(O_ITEM,1,(yyvsp[0].arg),Nullarg,Nullarg))); }
#line 2781 "perly.c" /* yacc.c:1646  */
    break;

  case 87:
#line 471 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { opargs[(yyvsp[-1].ival)] = 0;	/* force it special */
			    (yyval.arg) = make_op((yyvsp[-1].ival), 1,
				stab2arg(A_STAB,stabent((yyvsp[0].cval),TRUE)),
				Nullarg, Nullarg);
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch;
			}
#line 2792 "perly.c" /* yacc.c:1646  */
    break;

  case 88:
#line 478 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { opargs[(yyvsp[-1].ival)] = 1;
			    (yyval.arg) = make_op((yyvsp[-1].ival), 1, (yyvsp[0].arg), Nullarg, Nullarg); }
#line 2799 "perly.c" /* yacc.c:1646  */
    break;

  case 89:
#line 481 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { opargs[(yyvsp[0].ival)] = ((yyvsp[0].ival) != O_FTTTY);
			    (yyval.arg) = make_op((yyvsp[0].ival), 1,
				stab2arg(A_STAB,
				  (yyvsp[0].ival) == O_FTTTY?stabent("STDIN",TRUE):defstab),
				Nullarg, Nullarg); }
#line 2809 "perly.c" /* yacc.c:1646  */
    break;

  case 90:
#line 487 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(localize(make_op(O_ASSIGN, 1,
				localize(listish(make_list((yyvsp[-1].arg)))),
				Nullarg,Nullarg))); }
#line 2817 "perly.c" /* yacc.c:1646  */
    break;

  case 91:
#line 491 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_list((yyvsp[-1].arg)); }
#line 2823 "perly.c" /* yacc.c:1646  */
    break;

  case 92:
#line 493 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_list(Nullarg); }
#line 2829 "perly.c" /* yacc.c:1646  */
    break;

  case 93:
#line 495 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_DOFILE,2,(yyvsp[0].arg),Nullarg,Nullarg);
			  allstabs = TRUE;}
#line 2836 "perly.c" /* yacc.c:1646  */
    break;

  case 94:
#line 498 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = cmd_to_arg((yyvsp[0].cmdval)); }
#line 2842 "perly.c" /* yacc.c:1646  */
    break;

  case 95:
#line 500 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_STAB,(yyvsp[0].stabval)); }
#line 2848 "perly.c" /* yacc.c:1646  */
    break;

  case 96:
#line 502 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_STAR,(yyvsp[0].stabval)); }
#line 2854 "perly.c" /* yacc.c:1646  */
    break;

  case 97:
#line 504 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_AELEM, 2,
				stab2arg(A_STAB,aadd((yyvsp[-3].stabval))), (yyvsp[-1].arg), Nullarg); }
#line 2861 "perly.c" /* yacc.c:1646  */
    break;

  case 98:
#line 507 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_HASH, 1,
				stab2arg(A_STAB,(yyvsp[0].stabval)),
				Nullarg, Nullarg); }
#line 2869 "perly.c" /* yacc.c:1646  */
    break;

  case 99:
#line 511 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_ARRAY, 1,
				stab2arg(A_STAB,(yyvsp[0].stabval)),
				Nullarg, Nullarg); }
#line 2877 "perly.c" /* yacc.c:1646  */
    break;

  case 100:
#line 515 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_HELEM, 2,
				stab2arg(A_STAB,hadd((yyvsp[-4].stabval))),
				jmaybe((yyvsp[-2].arg)),
				Nullarg);
			    expectterm = FALSE; }
#line 2887 "perly.c" /* yacc.c:1646  */
    break;

  case 101:
#line 521 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_LSLICE, 3,
				Nullarg,
				listish(make_list((yyvsp[-1].arg))),
				listish(make_list((yyvsp[-4].arg)))); }
#line 2896 "perly.c" /* yacc.c:1646  */
    break;

  case 102:
#line 526 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_LSLICE, 3,
				Nullarg,
				listish(make_list((yyvsp[-1].arg))),
				Nullarg); }
#line 2905 "perly.c" /* yacc.c:1646  */
    break;

  case 103:
#line 531 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_ASLICE, 2,
				stab2arg(A_STAB,aadd((yyvsp[-3].stabval))),
				listish(make_list((yyvsp[-1].arg))),
				Nullarg); }
#line 2914 "perly.c" /* yacc.c:1646  */
    break;

  case 104:
#line 536 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_HSLICE, 2,
				stab2arg(A_STAB,hadd((yyvsp[-4].stabval))),
				listish(make_list((yyvsp[-2].arg))),
				Nullarg);
			    expectterm = FALSE; }
#line 2924 "perly.c" /* yacc.c:1646  */
    break;

  case 105:
#line 542 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_DELETE, 2,
				stab2arg(A_STAB,hadd((yyvsp[-4].stabval))),
				jmaybe((yyvsp[-2].arg)),
				Nullarg);
			    expectterm = FALSE; }
#line 2934 "perly.c" /* yacc.c:1646  */
    break;

  case 106:
#line 548 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_DELETE, 2,
				stab2arg(A_STAB,hadd((yyvsp[-5].stabval))),
				jmaybe((yyvsp[-3].arg)),
				Nullarg);
			    expectterm = FALSE; }
#line 2944 "perly.c" /* yacc.c:1646  */
    break;

  case 107:
#line 554 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_ARYLEN,(yyvsp[0].stabval)); }
#line 2950 "perly.c" /* yacc.c:1646  */
    break;

  case 108:
#line 556 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2956 "perly.c" /* yacc.c:1646  */
    break;

  case 109:
#line 558 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2962 "perly.c" /* yacc.c:1646  */
    break;

  case 110:
#line 560 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2968 "perly.c" /* yacc.c:1646  */
    break;

  case 111:
#line 562 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = (yyvsp[0].arg); }
#line 2974 "perly.c" /* yacc.c:1646  */
    break;

  case 112:
#line 564 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_WORD,stabent((yyvsp[-3].cval),MULTI)),
				make_list((yyvsp[-1].arg)),
				Nullarg); Safefree((yyvsp[-3].cval)); (yyvsp[-3].cval) = Nullch;
			    (yyval.arg)->arg_flags |= AF_DEPR; }
#line 2984 "perly.c" /* yacc.c:1646  */
    break;

  case 113:
#line 570 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_WORD,stabent((yyvsp[-3].cval),MULTI)),
				make_list((yyvsp[-1].arg)),
				Nullarg); Safefree((yyvsp[-3].cval)); (yyvsp[-3].cval) = Nullch; }
#line 2993 "perly.c" /* yacc.c:1646  */
    break;

  case 114:
#line 575 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_WORD,stabent((yyvsp[-2].cval),MULTI)),
				make_list(Nullarg),
				Nullarg);
			    Safefree((yyvsp[-2].cval)); (yyvsp[-2].cval) = Nullch;
			    (yyval.arg)->arg_flags |= AF_DEPR; }
#line 3004 "perly.c" /* yacc.c:1646  */
    break;

  case 115:
#line 582 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_WORD,stabent((yyvsp[-2].cval),MULTI)),
				make_list(Nullarg),
				Nullarg);
			    Safefree((yyvsp[-2].cval)); (yyvsp[-2].cval) = Nullch;
			}
#line 3015 "perly.c" /* yacc.c:1646  */
    break;

  case 116:
#line 589 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_WORD,stabent((yyvsp[0].cval),MULTI)),
				Nullarg,
				Nullarg);
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch;
			}
#line 3026 "perly.c" /* yacc.c:1646  */
    break;

  case 117:
#line 596 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_STAB,(yyvsp[-3].stabval)),
				make_list((yyvsp[-1].arg)),
				Nullarg);
			    (yyval.arg)->arg_flags |= AF_DEPR; }
#line 3036 "perly.c" /* yacc.c:1646  */
    break;

  case 118:
#line 602 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_STAB,(yyvsp[-3].stabval)),
				make_list((yyvsp[-1].arg)),
				Nullarg); }
#line 3045 "perly.c" /* yacc.c:1646  */
    break;

  case 119:
#line 607 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_STAB,(yyvsp[-2].stabval)),
				make_list(Nullarg),
				Nullarg);
			    (yyval.arg)->arg_flags |= AF_DEPR; }
#line 3055 "perly.c" /* yacc.c:1646  */
    break;

  case 120:
#line 613 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_STAB,(yyvsp[-2].stabval)),
				make_list(Nullarg),
				Nullarg); }
#line 3064 "perly.c" /* yacc.c:1646  */
    break;

  case 121:
#line 618 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((perldb ? O_DBSUBR : O_SUBR), 2,
				stab2arg(A_STAB,(yyvsp[0].stabval)),
				Nullarg,
				Nullarg); }
#line 3073 "perly.c" /* yacc.c:1646  */
    break;

  case 122:
#line 623 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[0].ival),0,Nullarg,Nullarg,Nullarg); }
#line 3079 "perly.c" /* yacc.c:1646  */
    break;

  case 123:
#line 625 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival),1,cval_to_arg((yyvsp[0].cval)),
			    Nullarg,Nullarg); }
#line 3086 "perly.c" /* yacc.c:1646  */
    break;

  case 124:
#line 628 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[0].ival),0,Nullarg,Nullarg,Nullarg); }
#line 3092 "perly.c" /* yacc.c:1646  */
    break;

  case 125:
#line 630 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival),1,cmd_to_arg((yyvsp[0].cmdval)),Nullarg,Nullarg); }
#line 3098 "perly.c" /* yacc.c:1646  */
    break;

  case 126:
#line 632 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival),1,(yyvsp[0].arg),Nullarg,Nullarg); }
#line 3104 "perly.c" /* yacc.c:1646  */
    break;

  case 127:
#line 634 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_SELECT, 0, Nullarg, Nullarg, Nullarg);}
#line 3110 "perly.c" /* yacc.c:1646  */
    break;

  case 128:
#line 636 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_SELECT, 1,
			    stab2arg(A_WORD,stabent((yyvsp[0].cval),TRUE)),
			    Nullarg,
			    Nullarg);
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch; }
#line 3120 "perly.c" /* yacc.c:1646  */
    break;

  case 129:
#line 642 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_SELECT, 1, (yyvsp[-1].arg), Nullarg, Nullarg); }
#line 3126 "perly.c" /* yacc.c:1646  */
    break;

  case 130:
#line 644 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { arg4 = (yyvsp[-1].arg);
			  (yyval.arg) = make_op(O_SSELECT, 4, (yyvsp[-4].arg), (yyvsp[-3].arg), (yyvsp[-2].arg)); }
#line 3133 "perly.c" /* yacc.c:1646  */
    break;

  case 131:
#line 647 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_OPEN, 2,
			    stab2arg(A_WORD,stabent((yyvsp[0].cval),TRUE)),
			    stab2arg(A_STAB,stabent((yyvsp[0].cval),TRUE)),
			    Nullarg);
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch;
			}
#line 3144 "perly.c" /* yacc.c:1646  */
    break;

  case 132:
#line 654 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_OPEN, 2,
			    stab2arg(A_WORD,stabent((yyvsp[-1].cval),TRUE)),
			    stab2arg(A_STAB,stabent((yyvsp[-1].cval),TRUE)),
			    Nullarg);
			    Safefree((yyvsp[-1].cval)); (yyvsp[-1].cval) = Nullch;
			}
#line 3155 "perly.c" /* yacc.c:1646  */
    break;

  case 133:
#line 661 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_OPEN, 2,
			    (yyvsp[-2].arg),
			    (yyvsp[-1].arg), Nullarg); }
#line 3163 "perly.c" /* yacc.c:1646  */
    break;

  case 134:
#line 665 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-3].ival), 1,
			    (yyvsp[-1].arg),
			    Nullarg, Nullarg); }
#line 3171 "perly.c" /* yacc.c:1646  */
    break;

  case 135:
#line 669 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival), 1,
			    stab2arg(A_WORD,stabent((yyvsp[0].cval),TRUE)),
			    Nullarg, Nullarg);
			  Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch; }
#line 3180 "perly.c" /* yacc.c:1646  */
    break;

  case 136:
#line 674 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival), 1,
			    stab2arg(A_STAB,(yyvsp[0].stabval)),
			    Nullarg, Nullarg); }
#line 3188 "perly.c" /* yacc.c:1646  */
    break;

  case 137:
#line 678 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-2].ival), 1,
			    stab2arg(A_WORD,Nullstab),
			    Nullarg, Nullarg); }
#line 3196 "perly.c" /* yacc.c:1646  */
    break;

  case 138:
#line 682 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[0].ival), 0,
			    Nullarg, Nullarg, Nullarg); }
#line 3203 "perly.c" /* yacc.c:1646  */
    break;

  case 139:
#line 685 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-4].ival), 2, (yyvsp[-2].arg), (yyvsp[-1].arg), Nullarg); }
#line 3209 "perly.c" /* yacc.c:1646  */
    break;

  case 140:
#line 687 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-5].ival), 3, (yyvsp[-3].arg), (yyvsp[-2].arg), make_list((yyvsp[-1].arg))); }
#line 3215 "perly.c" /* yacc.c:1646  */
    break;

  case 141:
#line 689 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-5].ival), 2, (yyvsp[-3].arg), (yyvsp[-1].arg), Nullarg); }
#line 3221 "perly.c" /* yacc.c:1646  */
    break;

  case 142:
#line 691 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { arg4 = (yyvsp[-1].arg); (yyval.arg) = make_op((yyvsp[-6].ival), 4, (yyvsp[-4].arg), (yyvsp[-3].arg), (yyvsp[-2].arg)); }
#line 3227 "perly.c" /* yacc.c:1646  */
    break;

  case 143:
#line 693 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { arg4 = (yyvsp[-2].arg); arg5 = (yyvsp[-1].arg);
			  (yyval.arg) = make_op((yyvsp[-8].ival), 5, (yyvsp[-6].arg), (yyvsp[-4].arg), (yyvsp[-3].arg)); }
#line 3234 "perly.c" /* yacc.c:1646  */
    break;

  case 144:
#line 696 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-5].ival), 2,
			    (yyvsp[-3].arg),
			    make_list((yyvsp[-1].arg)),
			    Nullarg); }
#line 3243 "perly.c" /* yacc.c:1646  */
    break;

  case 145:
#line 701 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_POP, 1, (yyvsp[0].arg), Nullarg, Nullarg); }
#line 3249 "perly.c" /* yacc.c:1646  */
    break;

  case 146:
#line 703 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_POP, 1, (yyvsp[-1].arg), Nullarg, Nullarg); }
#line 3255 "perly.c" /* yacc.c:1646  */
    break;

  case 147:
#line 705 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_SHIFT, 1, (yyvsp[0].arg), Nullarg, Nullarg); }
#line 3261 "perly.c" /* yacc.c:1646  */
    break;

  case 148:
#line 707 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_SHIFT, 1, (yyvsp[-1].arg), Nullarg, Nullarg); }
#line 3267 "perly.c" /* yacc.c:1646  */
    break;

  case 149:
#line 709 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op(O_SHIFT, 1,
			    stab2arg(A_STAB,
			      aadd(stabent(subline ? "_" : "ARGV", TRUE))),
			    Nullarg, Nullarg); }
#line 3276 "perly.c" /* yacc.c:1646  */
    break;

  case 150:
#line 714 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    {   static char p[]="/\\s+/";
			    char *oldend = bufend;
			    ARG *oldarg = yylval.arg;
			    
			    bufend=p+5;
			    (void)scanpat(p);
			    bufend=oldend;
			    (yyval.arg) = make_split(defstab,yylval.arg,Nullarg);
			    yylval.arg = oldarg; }
#line 3290 "perly.c" /* yacc.c:1646  */
    break;

  case 151:
#line 724 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = mod_match(O_MATCH, (yyvsp[-2].arg),
			  make_split(defstab,(yyvsp[-3].arg),(yyvsp[-1].arg)));}
#line 3297 "perly.c" /* yacc.c:1646  */
    break;

  case 152:
#line 727 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = mod_match(O_MATCH, (yyvsp[-1].arg),
			  make_split(defstab,(yyvsp[-2].arg),Nullarg) ); }
#line 3304 "perly.c" /* yacc.c:1646  */
    break;

  case 153:
#line 730 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = mod_match(O_MATCH,
			    stab2arg(A_STAB,defstab),
			    make_split(defstab,(yyvsp[-1].arg),Nullarg) ); }
#line 3312 "perly.c" /* yacc.c:1646  */
    break;

  case 154:
#line 734 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-4].ival), 2,
			    (yyvsp[-2].arg),
			    listish(make_list((yyvsp[-1].arg))),
			    Nullarg); }
#line 3321 "perly.c" /* yacc.c:1646  */
    break;

  case 155:
#line 739 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-3].ival), 1,
			    make_list((yyvsp[-1].arg)),
			    Nullarg,
			    Nullarg); }
#line 3330 "perly.c" /* yacc.c:1646  */
    break;

  case 156:
#line 744 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op((yyvsp[-1].ival), 1, fixl((yyvsp[-1].ival),(yyvsp[0].arg)),
			    Nullarg, Nullarg)); }
#line 3337 "perly.c" /* yacc.c:1646  */
    break;

  case 157:
#line 747 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = l(make_op((yyvsp[0].ival), 1,
			    stab2arg(A_STAB,defstab),
			    Nullarg, Nullarg)); }
#line 3345 "perly.c" /* yacc.c:1646  */
    break;

  case 158:
#line 751 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[0].ival), 0, Nullarg, Nullarg, Nullarg); }
#line 3351 "perly.c" /* yacc.c:1646  */
    break;

  case 159:
#line 753 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-2].ival), 0, Nullarg, Nullarg, Nullarg); }
#line 3357 "perly.c" /* yacc.c:1646  */
    break;

  case 160:
#line 755 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-2].ival), 0, Nullarg, Nullarg, Nullarg); }
#line 3363 "perly.c" /* yacc.c:1646  */
    break;

  case 161:
#line 757 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-3].ival), 1, (yyvsp[-1].arg), Nullarg, Nullarg); }
#line 3369 "perly.c" /* yacc.c:1646  */
    break;

  case 162:
#line 759 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-4].ival), 2, (yyvsp[-2].arg), (yyvsp[-1].arg), Nullarg);
			    if ((yyvsp[-4].ival) == O_INDEX && (yyval.arg)[2].arg_type == A_SINGLE)
				fbmcompile((yyval.arg)[2].arg_ptr.arg_str,0); }
#line 3377 "perly.c" /* yacc.c:1646  */
    break;

  case 163:
#line 763 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-4].ival), 2, (yyvsp[-2].arg), (yyvsp[-1].arg), Nullarg);
			    if ((yyvsp[-4].ival) == O_INDEX && (yyval.arg)[2].arg_type == A_SINGLE)
				fbmcompile((yyval.arg)[2].arg_ptr.arg_str,0); }
#line 3385 "perly.c" /* yacc.c:1646  */
    break;

  case 164:
#line 767 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-5].ival), 3, (yyvsp[-3].arg), (yyvsp[-2].arg), (yyvsp[-1].arg));
			    if ((yyvsp[-5].ival) == O_INDEX && (yyval.arg)[2].arg_type == A_SINGLE)
				fbmcompile((yyval.arg)[2].arg_ptr.arg_str,0); }
#line 3393 "perly.c" /* yacc.c:1646  */
    break;

  case 165:
#line 771 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-5].ival), 3, (yyvsp[-3].arg), (yyvsp[-2].arg), (yyvsp[-1].arg)); }
#line 3399 "perly.c" /* yacc.c:1646  */
    break;

  case 166:
#line 773 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { arg4 = (yyvsp[-1].arg);
			  (yyval.arg) = make_op((yyvsp[-6].ival), 4, (yyvsp[-4].arg), (yyvsp[-3].arg), (yyvsp[-2].arg)); }
#line 3406 "perly.c" /* yacc.c:1646  */
    break;

  case 167:
#line 776 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { arg4 = (yyvsp[-2].arg); arg5 = (yyvsp[-1].arg);
			  (yyval.arg) = make_op((yyvsp[-7].ival), 5, (yyvsp[-5].arg), (yyvsp[-4].arg), (yyvsp[-3].arg)); }
#line 3413 "perly.c" /* yacc.c:1646  */
    break;

  case 168:
#line 779 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-3].ival), 1,
				(yyvsp[-1].arg),
				Nullarg,
				Nullarg); }
#line 3422 "perly.c" /* yacc.c:1646  */
    break;

  case 169:
#line 784 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival), 1,
				(yyvsp[0].arg),
				Nullarg,
				Nullarg); }
#line 3431 "perly.c" /* yacc.c:1646  */
    break;

  case 170:
#line 789 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-5].ival), 3, (yyvsp[-3].arg), (yyvsp[-2].arg), (yyvsp[-1].arg)); }
#line 3437 "perly.c" /* yacc.c:1646  */
    break;

  case 173:
#line 795 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[0].ival),2,
				stab2arg(A_WORD,Nullstab),
				stab2arg(A_STAB,defstab),
				Nullarg); }
#line 3446 "perly.c" /* yacc.c:1646  */
    break;

  case 174:
#line 800 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival),2,
				stab2arg(A_WORD,Nullstab),
				maybelistish((yyvsp[-1].ival),make_list((yyvsp[0].arg))),
				Nullarg); }
#line 3455 "perly.c" /* yacc.c:1646  */
    break;

  case 175:
#line 805 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-1].ival),2,
				stab2arg(A_WORD,stabent((yyvsp[0].cval),TRUE)),
				stab2arg(A_STAB,defstab),
				Nullarg);
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch;
			}
#line 3466 "perly.c" /* yacc.c:1646  */
    break;

  case 176:
#line 812 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-2].ival),2,
				stab2arg(A_WORD,stabent((yyvsp[-1].cval),TRUE)),
				maybelistish((yyvsp[-2].ival),make_list((yyvsp[0].arg))),
				Nullarg); Safefree((yyvsp[-1].cval)); (yyvsp[-1].cval) = Nullch; }
#line 3475 "perly.c" /* yacc.c:1646  */
    break;

  case 177:
#line 817 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-2].ival),2,
				stab2arg(A_STAB,(yyvsp[-1].stabval)),
				maybelistish((yyvsp[-2].ival),make_list((yyvsp[0].arg))),
				Nullarg); }
#line 3484 "perly.c" /* yacc.c:1646  */
    break;

  case 178:
#line 822 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = make_op((yyvsp[-2].ival),2,
				cmd_to_arg((yyvsp[-1].cmdval)),
				maybelistish((yyvsp[-2].ival),make_list((yyvsp[0].arg))),
				Nullarg); }
#line 3493 "perly.c" /* yacc.c:1646  */
    break;

  case 179:
#line 829 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_WORD,stabent((yyvsp[0].cval),TRUE));
			  Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch;}
#line 3500 "perly.c" /* yacc.c:1646  */
    break;

  case 181:
#line 835 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_WORD,aadd(stabent((yyvsp[0].cval),TRUE)));
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch; }
#line 3507 "perly.c" /* yacc.c:1646  */
    break;

  case 182:
#line 838 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_STAB,(yyvsp[0].stabval)); }
#line 3513 "perly.c" /* yacc.c:1646  */
    break;

  case 183:
#line 842 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_WORD,hadd(stabent((yyvsp[0].cval),TRUE)));
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch; }
#line 3520 "perly.c" /* yacc.c:1646  */
    break;

  case 184:
#line 845 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.arg) = stab2arg(A_STAB,(yyvsp[0].stabval)); }
#line 3526 "perly.c" /* yacc.c:1646  */
    break;

  case 185:
#line 849 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.ival) = 1; }
#line 3532 "perly.c" /* yacc.c:1646  */
    break;

  case 186:
#line 851 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { (yyval.ival) = 0; }
#line 3538 "perly.c" /* yacc.c:1646  */
    break;

  case 187:
#line 860 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1646  */
    { char *s;
			    (yyval.arg) = op_new(1);
			    (yyval.arg)->arg_type = O_ITEM;
			    (yyval.arg)[1].arg_type = A_SINGLE;
			    (yyval.arg)[1].arg_ptr.arg_str = str_make((yyvsp[0].cval),0);
			    for (s = (yyvsp[0].cval); *s && isLOWER(*s); s++) ;
			    if (dowarn && !*s)
				warn(
				  "\"%s\" may clash with future reserved word",
				  (yyvsp[0].cval) );
			    Safefree((yyvsp[0].cval)); (yyvsp[0].cval) = Nullch;
			}
#line 3555 "perly.c" /* yacc.c:1646  */
    break;


#line 3559 "perly.c" /* yacc.c:1646  */
      default: break;
    }
  /* User semantic actions sometimes alter yychar, and that requires
     that yytoken be updated with the new translation.  We take the
     approach of translating immediately before every use of yytoken.
     One alternative is translating here after every semantic action,
     but that translation would be missed if the semantic action invokes
     YYABORT, YYACCEPT, or YYERROR immediately after altering yychar or
     if it invokes YYBACKUP.  In the case of YYABORT or YYACCEPT, an
     incorrect destructor might then be invoked immediately.  In the
     case of YYERROR or YYBACKUP, subsequent parser actions might lead
     to an incorrect destructor call or verbose syntax error message
     before the lookahead is translated.  */
  YY_SYMBOL_PRINT ("-> $$ =", yyr1[yyn], &yyval, &yyloc);

  YYPOPSTACK (yylen);
  yylen = 0;
  YY_STACK_PRINT (yyss, yyssp);

  *++yyvsp = yyval;

  /* Now 'shift' the result of the reduction.  Determine what state
     that goes to, based on the state we popped back to and the rule
     number reduced by.  */

  yyn = yyr1[yyn];

  yystate = yypgoto[yyn - YYNTOKENS] + *yyssp;
  if (0 <= yystate && yystate <= YYLAST && yycheck[yystate] == *yyssp)
    yystate = yytable[yystate];
  else
    yystate = yydefgoto[yyn - YYNTOKENS];

  goto yynewstate;


/*--------------------------------------.
| yyerrlab -- here on detecting error.  |
`--------------------------------------*/
yyerrlab:
  /* Make sure we have latest lookahead translation.  See comments at
     user semantic actions for why this is necessary.  */
  yytoken = yychar == YYEMPTY ? YYEMPTY : YYTRANSLATE (yychar);

  /* If not already recovering from an error, report this error.  */
  if (!yyerrstatus)
    {
      ++yynerrs;
#if ! YYERROR_VERBOSE
      yyerror (YY_("syntax error"));
#else
# define YYSYNTAX_ERROR yysyntax_error (&yymsg_alloc, &yymsg, \
                                        yyssp, yytoken)
      {
        char const *yymsgp = YY_("syntax error");
        int yysyntax_error_status;
        yysyntax_error_status = YYSYNTAX_ERROR;
        if (yysyntax_error_status == 0)
          yymsgp = yymsg;
        else if (yysyntax_error_status == 1)
          {
            if (yymsg != yymsgbuf)
              YYSTACK_FREE (yymsg);
            yymsg = (char *) YYSTACK_ALLOC (yymsg_alloc);
            if (!yymsg)
              {
                yymsg = yymsgbuf;
                yymsg_alloc = sizeof yymsgbuf;
                yysyntax_error_status = 2;
              }
            else
              {
                yysyntax_error_status = YYSYNTAX_ERROR;
                yymsgp = yymsg;
              }
          }
        yyerror (yymsgp);
        if (yysyntax_error_status == 2)
          goto yyexhaustedlab;
      }
# undef YYSYNTAX_ERROR
#endif
    }



  if (yyerrstatus == 3)
    {
      /* If just tried and failed to reuse lookahead token after an
         error, discard it.  */

      if (yychar <= YYEOF)
        {
          /* Return failure if at end of input.  */
          if (yychar == YYEOF)
            YYABORT;
        }
      else
        {
          yydestruct ("Error: discarding",
                      yytoken, &yylval);
          yychar = YYEMPTY;
        }
    }

  /* Else will try to reuse lookahead token after shifting the error
     token.  */
  goto yyerrlab1;


/*---------------------------------------------------.
| yyerrorlab -- error raised explicitly by YYERROR.  |
`---------------------------------------------------*/
yyerrorlab:

  /* Pacify compilers like GCC when the user code never invokes
     YYERROR and the label yyerrorlab therefore never appears in user
     code.  */
  if (/*CONSTCOND*/ 0)
     goto yyerrorlab;

  /* Do not reclaim the symbols of the rule whose action triggered
     this YYERROR.  */
  YYPOPSTACK (yylen);
  yylen = 0;
  YY_STACK_PRINT (yyss, yyssp);
  yystate = *yyssp;
  goto yyerrlab1;


/*-------------------------------------------------------------.
| yyerrlab1 -- common code for both syntax error and YYERROR.  |
`-------------------------------------------------------------*/
yyerrlab1:
  yyerrstatus = 3;      /* Each real token shifted decrements this.  */

  for (;;)
    {
      yyn = yypact[yystate];
      if (!yypact_value_is_default (yyn))
        {
          yyn += YYTERROR;
          if (0 <= yyn && yyn <= YYLAST && yycheck[yyn] == YYTERROR)
            {
              yyn = yytable[yyn];
              if (0 < yyn)
                break;
            }
        }

      /* Pop the current state because it cannot handle the error token.  */
      if (yyssp == yyss)
        YYABORT;


      yydestruct ("Error: popping",
                  yystos[yystate], yyvsp);
      YYPOPSTACK (1);
      yystate = *yyssp;
      YY_STACK_PRINT (yyss, yyssp);
    }

  YY_IGNORE_MAYBE_UNINITIALIZED_BEGIN
  *++yyvsp = yylval;
  YY_IGNORE_MAYBE_UNINITIALIZED_END


  /* Shift the error token.  */
  YY_SYMBOL_PRINT ("Shifting", yystos[yyn], yyvsp, yylsp);

  yystate = yyn;
  goto yynewstate;


/*-------------------------------------.
| yyacceptlab -- YYACCEPT comes here.  |
`-------------------------------------*/
yyacceptlab:
  yyresult = 0;
  goto yyreturn;

/*-----------------------------------.
| yyabortlab -- YYABORT comes here.  |
`-----------------------------------*/
yyabortlab:
  yyresult = 1;
  goto yyreturn;

#if !defined yyoverflow || YYERROR_VERBOSE
/*-------------------------------------------------.
| yyexhaustedlab -- memory exhaustion comes here.  |
`-------------------------------------------------*/
yyexhaustedlab:
  yyerror (YY_("memory exhausted"));
  yyresult = 2;
  /* Fall through.  */
#endif

yyreturn:
  if (yychar != YYEMPTY)
    {
      /* Make sure we have latest lookahead translation.  See comments at
         user semantic actions for why this is necessary.  */
      yytoken = YYTRANSLATE (yychar);
      yydestruct ("Cleanup: discarding lookahead",
                  yytoken, &yylval);
    }
  /* Do not reclaim the symbols of the rule whose action triggered
     this YYABORT or YYACCEPT.  */
  YYPOPSTACK (yylen);
  YY_STACK_PRINT (yyss, yyssp);
  while (yyssp != yyss)
    {
      yydestruct ("Cleanup: popping",
                  yystos[*yyssp], yyvsp);
      YYPOPSTACK (1);
    }
#ifndef yyoverflow
  if (yyss != yyssa)
    YYSTACK_FREE (yyss);
#endif
#if YYERROR_VERBOSE
  if (yymsg != yymsgbuf)
    YYSTACK_FREE (yymsg);
#endif
  return yyresult;
}
#line 873 "/work/benchmarks-baremetal/../interpreter-perl/perl-4.036/perly.y" /* yacc.c:1906  */
 /* PROGRAM */

// See LICENSE for license details.

//**************************************************************************
// Dhrystone bencmark
//--------------------------------------------------------------------------
//
// This is the classic Dhrystone synthetic integer benchmark.
//

#pragma GCC optimize ("no-inline")

#include "dhrystone.h"
#include "encoding.h"

void debug_printf(const char* str, ...);

#include "util.h"

#include <alloca.h>

// In setStats, we might trap reading uarch-specific counters.
// The trap handler will skip over the instruction and write 0,
// but only if a0 is the destination register.
#define read_csr_safe(reg) ({ register long __tmp asm("a0"); \
  asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
  __tmp; })

/* Global Variables: */

Rec_Pointer     Ptr_Glob[NUM_ROW],
                Next_Ptr_Glob[NUM_ROW];
int             Int_Glob[NUM_ROW];
Boolean         Bool_Glob[NUM_ROW];
char            Ch_1_Glob[NUM_ROW],
                Ch_2_Glob[NUM_ROW];

Enumeration     Func_1 ();
  /* forward declaration necessary since Enumeration may not simply be int */

#ifndef REG
        Boolean Reg = false;
#define REG
        /* REG becomes defined as empty */
        /* i.e. no register variables   */
#else
        Boolean Reg = true;
#undef REG
#define REG register
#endif

/* end of variables for time measurement */


int thread_entry(int cid, int nc)
/*****/
  /* main program, corresponds to procedures        */
  /* Main and Proc_0 in the Ada version             */
{
        One_Fifty       Int_1_Loc;
  REG   One_Fifty       Int_2_Loc;
        One_Fifty       Int_3_Loc;
  REG   char            Ch_Index;
        Enumeration     Enum_Loc;
        Str_30          Str_1_Loc;
        Str_30          Str_2_Loc;
  REG   int             Run_Index;
  REG   int             Number_Of_Runs;

  Boolean		Done;

  long            Begin_Time,
                  End_Time,
                  User_Time;
  long            Microseconds,
                  Dhrystones_Per_Second;
  int             Arr_1_Glob [50];
  int             Arr_2_Glob [50] [50];

  int coreId = read_csr_safe(mhartid);

  /* Arguments */
  Number_Of_Runs = NUMBER_OF_RUNS;

  /* Initializations */

  Next_Ptr_Glob[coreId] = (Rec_Pointer) alloca (sizeof (Rec_Type));
  Ptr_Glob[coreId] = (Rec_Pointer) alloca (sizeof (Rec_Type));

  Ptr_Glob[coreId]->Ptr_Comp                    = Next_Ptr_Glob[coreId];
  Ptr_Glob[coreId]->Discr                       = Ident_1;
  Ptr_Glob[coreId]->variant.var_1.Enum_Comp     = Ident_3;
  Ptr_Glob[coreId]->variant.var_1.Int_Comp      = 40;
  strcpy (Ptr_Glob[coreId]->variant.var_1.Str_Comp, 
          "DHRYSTONE PROGRAM, SOME STRING");
  strcpy (Str_1_Loc, "DHRYSTONE PROGRAM, 1'ST STRING");

  Arr_2_Glob [8][7] = 10;
        /* Was missing in published program. Without this statement,    */
        /* Arr_2_Glob [8][7] would have an undefined value.             */
        /* Warning: With 16-Bit processors and Number_Of_Runs > 32000,  */
        /* overflow may occur for this array element.                   */

  debug_printf("\n");
  debug_printf("Dhrystone Benchmark, Version %s\n", Version);
  if (Reg)
  {
    debug_printf("Program compiled with 'register' attribute\n");
  }
  else
  {
    debug_printf("Program compiled without 'register' attribute\n");
  }
  debug_printf("Using %s, HZ=%d\n", CLOCK_TYPE, HZ);
  debug_printf("\n");

  Done = false;
  while (!Done) {
    debug_printf("Trying %d runs through Dhrystone:\n", Number_Of_Runs);

    /***************/
    /* Start timer */
    /***************/

    setStats(1);
    Start_Timer();

    for (Run_Index = 1; Run_Index <= Number_Of_Runs; ++Run_Index)
    {

      Proc_5();
      Proc_4();
	/* Ch_1_Glob[coreId] == 'A', Ch_2_Glob[coreId] == 'B', Bool_Glob[coreId] == true */
      Int_1_Loc = 2;
      Int_2_Loc = 3;
      strcpy (Str_2_Loc, "DHRYSTONE PROGRAM, 2'ND STRING");
      Enum_Loc = Ident_2;
      Bool_Glob[coreId] = ! Func_2 (Str_1_Loc, Str_2_Loc);
	/* Bool_Glob[coreId] == 1 */
      while (Int_1_Loc < Int_2_Loc)  /* loop body executed once */
      {
	Int_3_Loc = 5 * Int_1_Loc - Int_2_Loc;
	  /* Int_3_Loc == 7 */
	Proc_7 (Int_1_Loc, Int_2_Loc, &Int_3_Loc);
	  /* Int_3_Loc == 7 */
	Int_1_Loc += 1;
      } /* while */
	/* Int_1_Loc == 3, Int_2_Loc == 3, Int_3_Loc == 7 */
      Proc_8 (Arr_1_Glob, Arr_2_Glob, Int_1_Loc, Int_3_Loc);
	/* Int_Glob[coreId] == 5 */
      Proc_1 (Ptr_Glob[coreId]);
      for (Ch_Index = 'A'; Ch_Index <= Ch_2_Glob[coreId]; ++Ch_Index)
			       /* loop body executed twice */
      {
	if (Enum_Loc == Func_1 (Ch_Index, 'C'))
	    /* then, not executed */
	  {
	  Proc_6 (Ident_1, &Enum_Loc);
	  strcpy (Str_2_Loc, "DHRYSTONE PROGRAM, 3'RD STRING");
	  Int_2_Loc = Run_Index;
	  Int_Glob[coreId] = Run_Index;
	  }
      }
	/* Int_1_Loc == 3, Int_2_Loc == 3, Int_3_Loc == 7 */
      Int_2_Loc = Int_2_Loc * Int_1_Loc;
      Int_1_Loc = Int_2_Loc / Int_3_Loc;
      Int_2_Loc = 7 * (Int_2_Loc - Int_3_Loc) - Int_1_Loc;
	/* Int_1_Loc == 1, Int_2_Loc == 13, Int_3_Loc == 7 */
      Proc_2 (&Int_1_Loc);
	/* Int_1_Loc == 5 */

    } /* loop "for Run_Index" */

    /**************/
    /* Stop timer */
    /**************/

    Stop_Timer();
    setStats(0);

    User_Time = End_Time - Begin_Time;

    if (User_Time < Too_Small_Time)
    {
      printf("Measured time too small to obtain meaningful results\n");
      Number_Of_Runs = Number_Of_Runs * 10;
      printf("\n");
    } else Done = true;
  }

  debug_printf("Final values of the variables used in the benchmark:\n");
  debug_printf("\n");
  debug_printf("Int_Glob[coreId]:            %d\n", Int_Glob[coreId]);
  debug_printf("        should be:   %d\n", 5);
  debug_printf("Bool_Glob[coreId]:           %d\n", Bool_Glob[coreId]);
  debug_printf("        should be:   %d\n", 1);
  debug_printf("Ch_1_Glob[coreId]:           %c\n", Ch_1_Glob[coreId]);
  debug_printf("        should be:   %c\n", 'A');
  debug_printf("Ch_2_Glob[coreId]:           %c\n", Ch_2_Glob[coreId]);
  debug_printf("        should be:   %c\n", 'B');
  debug_printf("Arr_1_Glob[8]:       %d\n", Arr_1_Glob[8]);
  debug_printf("        should be:   %d\n", 7);
  debug_printf("Arr_2_Glob[8][7]:    %d\n", Arr_2_Glob[8][7]);
  debug_printf("        should be:   Number_Of_Runs + 10\n");
  debug_printf("Ptr_Glob[coreId]->\n");
  debug_printf("  Ptr_Comp:          %d\n", (long) Ptr_Glob[coreId]->Ptr_Comp);
  debug_printf("        should be:   (implementation-dependent)\n");
  debug_printf("  Discr:             %d\n", Ptr_Glob[coreId]->Discr);
  debug_printf("        should be:   %d\n", 0);
  debug_printf("  Enum_Comp:         %d\n", Ptr_Glob[coreId]->variant.var_1.Enum_Comp);
  debug_printf("        should be:   %d\n", 2);
  debug_printf("  Int_Comp:          %d\n", Ptr_Glob[coreId]->variant.var_1.Int_Comp);
  debug_printf("        should be:   %d\n", 17);
  debug_printf("  Str_Comp:          %s\n", Ptr_Glob[coreId]->variant.var_1.Str_Comp);
  debug_printf("        should be:   DHRYSTONE PROGRAM, SOME STRING\n");
  debug_printf("Next_Ptr_Glob[coreId]->\n");
  debug_printf("  Ptr_Comp:          %d\n", (long) Next_Ptr_Glob[coreId]->Ptr_Comp);
  debug_printf("        should be:   (implementation-dependent), same as above\n");
  debug_printf("  Discr:             %d\n", Next_Ptr_Glob[coreId]->Discr);
  debug_printf("        should be:   %d\n", 0);
  debug_printf("  Enum_Comp:         %d\n", Next_Ptr_Glob[coreId]->variant.var_1.Enum_Comp);
  debug_printf("        should be:   %d\n", 1);
  debug_printf("  Int_Comp:          %d\n", Next_Ptr_Glob[coreId]->variant.var_1.Int_Comp);
  debug_printf("        should be:   %d\n", 18);
  debug_printf("  Str_Comp:          %s\n",
                                Next_Ptr_Glob[coreId]->variant.var_1.Str_Comp);
  debug_printf("        should be:   DHRYSTONE PROGRAM, SOME STRING\n");
  debug_printf("Int_1_Loc:           %d\n", Int_1_Loc);
  debug_printf("        should be:   %d\n", 5);
  debug_printf("Int_2_Loc:           %d\n", Int_2_Loc);
  debug_printf("        should be:   %d\n", 13);
  debug_printf("Int_3_Loc:           %d\n", Int_3_Loc);
  debug_printf("        should be:   %d\n", 7);
  debug_printf("Enum_Loc:            %d\n", Enum_Loc);
  debug_printf("        should be:   %d\n", 1);
  debug_printf("Str_1_Loc:           %s\n", Str_1_Loc);
  debug_printf("        should be:   DHRYSTONE PROGRAM, 1'ST STRING\n");
  debug_printf("Str_2_Loc:           %s\n", Str_2_Loc);
  debug_printf("        should be:   DHRYSTONE PROGRAM, 2'ND STRING\n");
  debug_printf("\n");


  Microseconds = ((User_Time / Number_Of_Runs) * Mic_secs_Per_Second) / HZ;
  Dhrystones_Per_Second = (HZ * Number_Of_Runs) / User_Time;

  printf("Microseconds for one run through Dhrystone: %ld\n", Microseconds);
  printf("Dhrystones per Second:                      %ld\n", Dhrystones_Per_Second);

  return 0;
}


Proc_1 (Ptr_Val_Par)
/******************/

REG Rec_Pointer Ptr_Val_Par;
    /* executed once */
{
  int coreId = read_csr_safe(mhartid);
  REG Rec_Pointer Next_Record = Ptr_Val_Par->Ptr_Comp;  
                                        /* == Ptr_Glob_Next */
  /* Local variable, initialized with Ptr_Val_Par->Ptr_Comp,    */
  /* corresponds to "rename" in Ada, "with" in Pascal           */
  
  structassign (*Ptr_Val_Par->Ptr_Comp, *Ptr_Glob[coreId]); 
  Ptr_Val_Par->variant.var_1.Int_Comp = 5;
  Next_Record->variant.var_1.Int_Comp 
        = Ptr_Val_Par->variant.var_1.Int_Comp;
  Next_Record->Ptr_Comp = Ptr_Val_Par->Ptr_Comp;
  Proc_3 (&Next_Record->Ptr_Comp);
    /* Ptr_Val_Par->Ptr_Comp->Ptr_Comp 
                        == Ptr_Glob[coreId]->Ptr_Comp */
  if (Next_Record->Discr == Ident_1)
    /* then, executed */
  {
    Next_Record->variant.var_1.Int_Comp = 6;
    Proc_6 (Ptr_Val_Par->variant.var_1.Enum_Comp, 
           &Next_Record->variant.var_1.Enum_Comp);
    Next_Record->Ptr_Comp = Ptr_Glob[coreId]->Ptr_Comp;
    Proc_7 (Next_Record->variant.var_1.Int_Comp, 10, 
           &Next_Record->variant.var_1.Int_Comp);
  }
  else /* not executed */
    structassign (*Ptr_Val_Par, *Ptr_Val_Par->Ptr_Comp);
} /* Proc_1 */


Proc_2 (Int_Par_Ref)
/******************/
    /* executed once */
    /* *Int_Par_Ref == 1, becomes 4 */

One_Fifty   *Int_Par_Ref;
{
  One_Fifty  Int_Loc;  
  Enumeration   Enum_Loc;
  int coreId = read_csr_safe(mhartid);

  Int_Loc = *Int_Par_Ref + 10;
  do /* executed once */
    if (Ch_1_Glob[coreId] == 'A')
      /* then, executed */
    {
      Int_Loc -= 1;
      *Int_Par_Ref = Int_Loc - Int_Glob[coreId];
      Enum_Loc = Ident_1;
    } /* if */
  while (Enum_Loc != Ident_1); /* true */
} /* Proc_2 */


Proc_3 (Ptr_Ref_Par)
/******************/
    /* executed once */
    /* Ptr_Ref_Par becomes Ptr_Glob[coreId] */

Rec_Pointer *Ptr_Ref_Par;

{
  int coreId = read_csr_safe(mhartid);
  if (Ptr_Glob[coreId] != Null)
    /* then, executed */
    *Ptr_Ref_Par = Ptr_Glob[coreId]->Ptr_Comp;
  Proc_7 (10, Int_Glob[coreId], &Ptr_Glob[coreId]->variant.var_1.Int_Comp);
} /* Proc_3 */


Proc_4 () /* without parameters */
/*******/
    /* executed once */
{
  Boolean Bool_Loc;
  int coreId = read_csr_safe(mhartid);

  Bool_Loc = Ch_1_Glob[coreId] == 'A';
  Bool_Glob[coreId] = Bool_Loc | Bool_Glob[coreId];
  Ch_2_Glob[coreId] = 'B';
} /* Proc_4 */


Proc_5 () /* without parameters */
/*******/
    /* executed once */
{
  int coreId = read_csr_safe(mhartid);
  Ch_1_Glob[coreId] = 'A';
  Bool_Glob[coreId] = false;
} /* Proc_5 */

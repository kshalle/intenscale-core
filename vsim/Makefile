#=======================================================================
# Makefile for Verilog simulation w/ VCS
#-----------------------------------------------------------------------
# Yunsup Lee (yunsup@cs.berkeley.edu)
#
# This makefile will build a rtl simulator and run various tests to
# verify proper functionality.
#

WITH_WAVE    ?= 0
IS_SDF_SIM   ?= 0
GATE_SIM     ?= 0
CLOCK_PERIOD ?= 2.0
INP_DELAY    ?= 1.0
TEST_NAME    ?= median

#-----------------------------------------------------------------------
# Site-specific paths
#
# Override these on the make command line or in your environment. None of
# the content under these roots is distributed with this repository.
#
#   TSMC_PDK     Root of the TSMC 28nm HPM kit -- the directory that
#                contains digital/ and sram/. Required when GATE_SIM=1.
#   BACKEND_DIR  Directory holding backend results: the post-place-and-route
#                netlist (Innovus) and the back-annotated timing file
#                (Tempus). Required when IS_SDF_SIM=1.
#
# Example:
#   make ncsim GATE_SIM=1 TSMC_PDK=/opt/pdk/tcbn28hpm/TSMCHOME
#-----------------------------------------------------------------------
TSMC_PDK     ?= /path/to/tsmc/tcbn28hpm/TSMCHOME
BACKEND_DIR  ?= /path/to/backend/outputs

# Post-P&R netlist. Either a bare filename in this directory, or an
# absolute path -- e.g. $(BACKEND_DIR)/RocketTile_final.v
NETLIST      ?= RocketTile_final.v

# Back-annotated timing, used only when IS_SDF_SIM=1.
SDF_FILE     ?= \"$(BACKEND_DIR)/RocketTile_TYPICAL_VIEW.sdf\"

# Vendor simulation models linked in for gate-level runs.
#
# This is intentionally empty: the standard-cell and memory-compiler
# instances a gate-level run needs depend on the kit and on how the design
# was hardened, and naming a particular set here would describe one site's
# backend flow rather than anything this repository requires.
#
# For GATE_SIM=1, set this to the behavioural models your kit provides --
# the standard-cell libraries the netlist instantiates, plus a model for
# each compiled memory -- or leave it empty and populate SOURCE yourself.
#
#   make ncsim GATE_SIM=1 TSMC_PDK=... TSMC_SIM_MODELS="a.v b.v"
TSMC_SIM_MODELS ?=

GATE_SIM_OPT = 
ifeq ($(IS_SDF_SIM), 1)
GATE_SIM_OPT = -sdf_verbose +define+SDF_FILE=$(SDF_FILE)
GATE_SIM     = 1
endif

ifeq ($(GATE_SIM), 1)
ifeq ($(IS_SDF_SIM), 0)
GATE_SIM_OPT = -define UNIT_DELAY -sequdp_nba_delay -add_seq_delay 10ps
endif
endif

ifeq ($(WITH_WAVE), 1)
  DO_FILE ?= testNcWave.do
  ACC_FLAGS = -access +r
else
  DO_FILE ?= testNc.do
  ACC_FLAGS = 
endif

ifeq ($(GATE_SIM), 1)
ifeq ($(wildcard $(TSMC_PDK)),)
$(error GATE_SIM=1 requires TSMC_PDK to point at the TSMC 28nm HPM kit -- got '$(TSMC_PDK)')
endif
SOURCE = "\
		$(sim_vsrcs) \
		$(TSMC_SIM_MODELS) \
		$(NETLIST) \
		"
else
SOURCE = $(sim_vsrcs)
endif

default: all

base_dir = $(abspath ..)
generated_dir = $(abspath ./generated-src)
mem_gen = $(VLSI_MEM_GEN)
sim_dir = .
output_dir = $(sim_dir)/output

BACKEND ?= v
TB ?= TestDriver

include $(base_dir)/Makefrag
include $(sim_dir)/Makefrag
ifneq ($(filter run% %.run %.out %.vpd %.vcd,$(MAKECMDGOALS)),)
-include $(generated_dir)/$(long_name).d
endif
include $(base_dir)/vsim/Makefrag-verilog

all: $(simv)
debug: $(simv_debug)

ncsim:
	#cp generated-src/consts.DefaultConfig.vh generated-src/consts.DefaultConfig.h
	#sed -i 's/`/#/g' generated-src/consts.DefaultConfig.h
	#sed -i 's/#0.1/#0.05/g' generated-src/Top.DefaultConfig.tb.vfrag
	#sed -i 's/#1/#0.05/g' generated-src/Top.DefaultConfig.tb.vfrag
	xrun -timescale 1ns/1ps $(ACC_FLAGS) -64 +sv -debug -I../riscv-tools/include -I./generated-src -Wcxx -std=c++11 -verbose -ccargs ../riscv-tools/lib ../riscv-tools/lib/libfesvr.so -incdir ./generated-src +define+CLOCK_PERIOD=$(CLOCK_PERIOD) +define+INP_DELAY=$(INP_DELAY) $(SOURCE) $(sim_csrcs) -DNO_STORAGE -DNO_OUTPUT $(GATE_SIM_OPT) -Dmain=nomain +max-cycles=100000000 +verbose +define+PRINTF_COND=$(TB).printf_cond +define+STOP_COND=!$(TB).reset +define+RANDOMIZE_MEM_INIT +define+RANDOMIZE_REG_INIT +define+RANDOMIZE_GARBAGE_ASSIGN +define+RANDOMIZE_INVALID_ASSIGN +libext+.v -DTEST_NAME=$(TEST_NAME) -input $(DO_FILE) -logfile transcript -ALLOWREDEFINITION -top $(TB) -gui 2> output/$(TEST_NAME).riscv.out

clean:
	rm -rf $(junk) simv* csrc *.key DVE* *.h *.a *.daidir INCA_libs irun.history *~ irun.log ncsc.log *.vcd waves $(generated_dir)

.PHONY: default all debug clean


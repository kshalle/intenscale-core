default: all

base_dir = $(abspath ..)
sim_dir = .

include $(base_dir)/Makefrag

# DRAMSIM3_SUFFIX (see top-level Makefrag) keeps the DRAMSIM3=0 flow's
# generated Verilog/object files and test results separate from the default
# (DRAMSIM3=1) flow, since they elaborate different hardware (different
# memory-model module) and would otherwise overwrite each other's output.
generated_dir = $(abspath ./generated-src$(DRAMSIM3_SUFFIX))
generated_dir_debug = $(abspath ./generated-src-debug$(DRAMSIM3_SUFFIX))
output_dir = $(sim_dir)/output$(DRAMSIM3_SUFFIX)

DRAMSIM3_HOME := $(abspath $(base_dir)/DRAMSIM3)

CXXSRCS := emulator SimDTM SimJTAG remote_bitbang ram compress elfloader
CXXFLAGS := $(CXXFLAGS) -std=c++11 -I$(RISCV)/include
LDFLAGS := $(LDFLAGS) -L$(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -L$(abspath $(sim_dir)) -lfesvr -lpthread
LDFLAGS += -lzstd -lz

# ram.h/ram.cc include DRAMSim3's common.h unconditionally (a generic utility
# header, not itself DRAMSim3-timing-specific), so this include path is needed
# regardless of DRAMSIM3 below.
CXXFLAGS += -I$(DRAMSIM3_HOME)/src

# See DRAMSIM3 in the top-level Makefrag: only link/enable the DRAMSim3
# timing model when it's actually selected, so the DRAMSIM3=0 flow doesn't
# need the DRAMSim3 library or its config files at all.
ifneq ($(DRAMSIM3),0)
LDFLAGS += -L $(DRAMSIM3_HOME)/build -ldramsim3 -Wl,-rpath-link=$(DRAMSIM3_HOME)/build/libdramsim3.so
CXXFLAGS += -DWITH_DRAMSIM3 -DDRAMSIM3_CONFIG=\\\"$(DRAMSIM3_HOME)/configs/intenscore_DDR4_8Gb_x8_3200_1ch_2ra_16GB.ini\\\" -DDRAMSIM3_OUTDIR=\\\"./\\\"
endif

emu = emulator-$(PROJECT)-$(CONFIG)$(DRAMSIM3_SUFFIX)
emu_debug = emulator-$(PROJECT)-$(CONFIG)-debug$(DRAMSIM3_SUFFIX)

include $(sim_dir)/Makefrag-verilator

all: $(emu)
debug: $(emu_debug)

clean:
	rm -rf *.o *.a emulator-* $(generated_dir) $(generated_dir_debug) DVEfiles $(output_dir)

# Generic "-nodramsim3" variant of any target, e.g.:
#   make run-bmark-tests-nodramsim3
#   make run-asm-tests-nodramsim3
#   make debug-nodramsim3
# Equivalent to passing DRAMSIM3=0 directly, just easier to remember.
%-nodramsim3:
	$(MAKE) DRAMSIM3=0 $*

.PHONY: default all debug clean

#--------------------------------------------------------------------
# Run assembly tests and benchmarks
#--------------------------------------------------------------------

# extra benchmark test
rvi-bmark-tests = intMul.riscv 

ifneq ($(filter run% %.run %.out %.vpd %.vcd,$(MAKECMDGOALS)),)
dummy := $(shell sed -i 's/rvi-bmark-tests = /rvi-bmark-tests += /g' $(generated_dir)/$(long_name).d)
-include $(generated_dir)/$(long_name).d
endif

# Cache/AMO stress tests
torture-tests = coh_checker.riscv amoadd_simple.riscv \
	amoadd.riscv amoor.riscv amoand.riscv amomin.riscv amomax.riscv \
	amoxor.riscv amominu.riscv amomaxu.riscv amoswap.riscv \
	amolrdscd.riscv amo-megazord.riscv interrupts.riscv \
	workqueue.riscv combined.riscv
	
$(patsubst %, $(output_dir)/%.hex, $(torture-tests)): $(output_dir)/%.hex: $(base_dir)/riscv-tests/torture/%.hex
	mkdir -p $(output_dir)
	ln -fs $< $@

$(addprefix $(output_dir)/, $(torture-tests)): $(output_dir)/%: $(base_dir)/riscv-tests/torture/%
	mkdir -p $(output_dir)
	ln -fs $< $@
	
run-torture-tests: $(patsubst %, $(output_dir)/%.out, $(torture-tests))
run-torture-tests-debug: $(patsubst %, $(output_dir)/%.vcd, $(torture-tests))
run-torture-tests-fst: $(patsubst %, $(output_dir)/%.fst, $(torture-tests))
run-torture-tests-fast: $(patsubst %, $(output_dir)/%.run, $(torture-tests))

$(output_dir)/%.run: $(output_dir)/% $(emu)
	./$(emu) +max-cycles=$(timeout_cycles) $< 2> /dev/null 2> $@ && [ $$PIPESTATUS -eq 0 ]

$(output_dir)/%.out: $(output_dir)/% $(emu)
	./$(emu) +max-cycles=$(timeout_cycles) +verbose $< $(disasm) $@ && [ $$PIPESTATUS -eq 0 ]

$(output_dir)/%.vcd: $(output_dir)/% $(emu_debug)
	./$(emu_debug) +max-cycles=$(timeout_cycles) +verbose -v$@ $< $(disasm) $(patsubst %.vcd,%.out,$@) && [ $$PIPESTATUS -eq 0 ]

$(output_dir)/%.vpd: $(output_dir)/% $(emu_debug)
	rm -rf $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	./$(emu_debug) +max-cycles=$(timeout_cycles) +verbose -v$@.vcd $< $(disasm) $(patsubst %.vpd,%.out,$@) && [ $$PIPESTATUS -eq 0 ]

$(output_dir)/%.fst: $(output_dir)/% $(emu_debug)
	rm -rf $@.vcd && mkfifo $@.vcd
	vcd2fst -Z $@.vcd $@ &
	./$(emu_debug) +max-cycles=$(timeout_cycles) +verbose -v$@.vcd $< $(disasm) $(patsubst %.fst,%.out,$@) && [ $$PIPESTATUS -eq 0 ]

run: run-asm-tests run-bmark-tests
run-debug: run-asm-tests-debug run-bmark-tests-debug
run-fast: run-asm-tests-fast run-bmark-tests-fast

.PHONY: run-asm-tests run-bmark-tests
.PHONY: run-asm-tests-debug run-bmark-tests-debug
.PHONY: run run-debug run-fast

.PHONY: run-torture-tests run-torture-tests-debug
.PHONY: run-torture-tests-fst run-torture-tests-fast

#--------------------------------------------------------------------
# spec95-equivalent bare-metal benchmarks (riscv-tests/spec95-equivalent/README.md)
#--------------------------------------------------------------------
# Every hart runs one complete copy of each benchmark ("rate" mode), so the
# binaries are built for this config's hart count. Per test, in
# output/spec95-equivalent/ (<name> = <test>-<workload>-<harts>):
#   <name>.log        everything the emulator prints (DRAMSim3's messages,
#                     PASSED/FAILED, the per-hart R_ counter lines, the trace
#                     with SPEC95EQ_VERBOSE=1), written as the run goes
#   <name>.build.log  the build's output
#   <name>.verdict    first line "RESULT: CORRECT" only if the emulator
#                     passed, every hart finished its copy and every copy's
#                     output matches the native reference; a "perf:" line
#                     with IPC/CPI per copy and the throughput IPC, and a
#                     "time:" line with the wall-clock time and sim speed
#   <name>/           the run directory: each copy's output files and
#                     DRAMSim3's statistics (dramsim3epoch.json)
#
#   make run-spec95-equivalent-tests SPEC95EQ_WORKLOAD=extra_tiny
#   make -j6 run-spec95-equivalent-tests CONFIG=freechips.rocketchip.system.Inten1RowConfig
spec95eq_dir = $(base_dir)/riscv-tests/spec95-equivalent
SPEC95EQ_WORKLOAD ?= tiny
SPEC95EQ_TESTS ?= libcint miniweather perl-perl4 bsmbench xlisp gcc-cc1
SPEC95EQ_NHARTS_Inten1RowConfig = 1
SPEC95EQ_NHARTS_Inten1CoreConfig = 16
SPEC95EQ_NHARTS_Inten2CoreConfig = 32
SPEC95EQ_NHARTS_Inten4CoreConfig = 64
SPEC95EQ_NHARTS ?= $(or $(SPEC95EQ_NHARTS_$(lastword $(subst ., ,$(CONFIG)))),16)
SPEC95EQ_EMU ?= $(emu)
SPEC95EQ_VERBOSE ?= 0
spec95eq_timeout_cycles ?= 5000000000
spec95eq_tag = $(SPEC95EQ_WORKLOAD)-$(SPEC95EQ_NHARTS)
spec95eq_out = $(abspath $(output_dir))/spec95-equivalent

define spec95eq_template
$(spec95eq_out)/$(1)-$$(spec95eq_tag).verdict: $$(SPEC95EQ_EMU)
	@mkdir -p $(spec95eq_out)
	@echo "spec95-equivalent $(1)-$$(spec95eq_tag): building  (log: $(spec95eq_out)/$(1)-$$(spec95eq_tag).build.log)"
	@$$(MAKE) -s -C $(spec95eq_dir) $(1) WORKLOAD=$$(SPEC95EQ_WORKLOAD) NHARTS=$$(SPEC95EQ_NHARTS) \
	  > $(spec95eq_out)/$(1)-$$(spec95eq_tag).build.log 2>&1 \
	  || { echo "spec95-equivalent $(1)-$$(spec95eq_tag): BUILD FAILED, see $(spec95eq_out)/$(1)-$$(spec95eq_tag).build.log"; exit 1; }
	@echo "spec95-equivalent $(1)-$$(spec95eq_tag): running   (log: $(spec95eq_out)/$(1)-$$(spec95eq_tag).log)"
	@LOGFILE=$(spec95eq_out)/$(1)-$$(spec95eq_tag).log VERBOSE=$$(SPEC95EQ_VERBOSE) \
	  $(spec95eq_dir)/scripts/run.sh $(1) $$(SPEC95EQ_WORKLOAD) $$(SPEC95EQ_NHARTS) \
	  $$(abspath $$(SPEC95EQ_EMU)) $(spec95eq_out)/$(1)-$$(spec95eq_tag) $$(spec95eq_timeout_cycles); \
	  rc=$$$$?; cp $(spec95eq_out)/$(1)-$$(spec95eq_tag)/$(1).verdict $$@; exit $$$$rc
endef
$(foreach t,$(SPEC95EQ_TESTS),$(eval $(call spec95eq_template,$(t))))

run-spec95-equivalent-tests: $(foreach t,$(SPEC95EQ_TESTS),$(spec95eq_out)/$(t)-$(spec95eq_tag).verdict)
	@echo "--- spec95-equivalent $(spec95eq_tag):"; for f in $^; do head -n1 $$f; grep -h '^perf:\|^time:' $$f | sed 's/^/    /'; done

.PHONY: run-spec95-equivalent-tests

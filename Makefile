required_branch=master_sync
DockerCopy:
ifeq ($(BRANCH_NAME),$(required_branch))
DockerCopy: doCopy
else
DockerCopy: doNothing
endif
doCopy:
	mkdir -p ../build
	rm -rf ../build/master_sync || echo its ok
	git clean -fxd
	cp -r   $(WORKSPACE) ../build/master_sync 
	cp Dockerfile ../build
	cd ../build && docker build . -t rocket_`date -I`
doNothing:
	@echo branch is $(GIT_BRANCH)
	@echo BRANCH= $(BRANCH_NAME)
	@echo WorkSpace $(WORKSPACE)

# =====================================================================
# Local build/run driver
#
# Everything below wraps the emulator/ and vsim/ build systems (and,
# for the legacy full rocket-chip regression harness, regression/) so
# the core can be elaborated and simulated from the top level instead
# of cd'ing into a subdirectory. See "Building and running the core"
# in README.md for prerequisites (RISCV toolchain, set_env.sh) and for
# what each target actually does.
#
# All of these require the RISCV environment variable to be set
# first (see set_env.sh); emulator/Makefrag enforces this and will
# error out with a pointer back to README.md if it isn't.
# =====================================================================

.PHONY: help build debug verilator \
	run run-debug run-fast \
	run-asm-tests run-bmark-tests run-torture-tests \
	vsim-verilog vsim-debug \
	regression \
	clean distclean

help:
	@echo "intenscale-core top-level targets:"
	@echo "  build              build the fast (non-debug) Verilator emulator"
	@echo "  debug              build the waveform-tracing debug emulator"
	@echo "  verilator          build/install the pinned Verilator toolchain only"
	@echo "  run                run the asm + benchmark tests against the fast emulator"
	@echo "  run-debug          same as run, but produces VCD/VPD waveforms"
	@echo "  run-fast           run tests without waveforms or disassembly (fastest)"
	@echo "  run-torture-tests  run the cache/AMO torture tests"
	@echo "  vsim-verilog       elaborate Verilog for the VCS/Xcelium flow (vsim/)"
	@echo "  vsim-debug         build the VCS waveform-tracing simulator (vsim/)"
	@echo "  regression         run the full rocket-chip regression suite (needs SUITE=...)"
	@echo "  clean              remove emulator/ and vsim/ build outputs"
	@echo "  distclean          clean, plus the fetched/built Verilator tree in emulator/verilator"
	@echo
	@echo "See README.md for the RISCV toolchain setup these all depend on."

build:
	$(MAKE) -C emulator all

debug:
	$(MAKE) -C emulator debug

verilator:
	$(MAKE) -C emulator verilator

run:
	$(MAKE) -C emulator run

run-debug:
	$(MAKE) -C emulator run-debug

run-fast:
	$(MAKE) -C emulator run-fast

run-asm-tests:
	$(MAKE) -C emulator run-asm-tests

run-bmark-tests:
	$(MAKE) -C emulator run-bmark-tests

run-torture-tests:
	$(MAKE) -C emulator run-torture-tests

vsim-verilog:
	$(MAKE) -C vsim verilog

vsim-debug:
	$(MAKE) -C vsim debug

regression:
	$(MAKE) -C regression regression

clean:
	$(MAKE) -C emulator clean
	$(MAKE) -C vsim clean

distclean: clean
	rm -rf emulator/verilator

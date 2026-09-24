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

.PHONY: sim

sim:
	$(MAKE) -C ./sim
	sbt "runMain set.SetSimulation"
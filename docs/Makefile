# Makefile for generating documentation

.PHONY: html clean

html:
	doxygen Doxyfile
	sphinx-build -b html source build/html

clean:
	rm -rf build/ doxygen/

docs:
	cd docs && make html

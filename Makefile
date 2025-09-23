# Makefile

# source files
CPPS = src/cpp/signal.cpp src/cpp/cau_stl_atom.cpp src/cpp/cau_interval_transducer.cpp src/cpp/stl_causation_opt.cpp src/cpp/stl_eval_mex_pw.cpp
HEADERS = src/include/signal.h src/include/transducer.h
MFILES = src/compile/compile_stl_mex.m

# target directories
DST_CPP = breach/Online/src/
DST_H   = breach/Online/include/
DST_M   = breach/Online/m_src/

# default
all: copy_cpp copy_h copy_m

# copy .cpp
copy_cpp: $(CPPS)
	cp $(CPPS) $(DST_CPP)

# copy .h
copy_h: $(HEADERS)
	cp $(HEADERS) $(DST_H)

# copy .m
copy_m: $(MFILES)
	cp $(MFILES) $(DST_M)

# 可选：清理
clean:
	@echo "Nothing to clean"


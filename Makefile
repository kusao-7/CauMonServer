# Makefile

# source files
CPPS = signal.cpp cau_stl_atom.cpp cau_interval_transducer.cpp stl_causation_opt.cpp stl_eval_mex_pw.cpp
HEADERS = signal.h transducer.h
MFILES = compile_stl_mex.m

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


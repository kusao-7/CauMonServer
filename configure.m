mex -setup;
addpath(genpath('.'));
cd breach/;
InitBreach;
compile_stl_mex;
cd ..;
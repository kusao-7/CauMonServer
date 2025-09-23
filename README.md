# CauMon: An Informative Online Monitor for Signal Temporal Logic

This repository is for the artifact evaluation of the paper ""

## Run in your local machine

### System requirement

- Operating system: Linux or MacOS;

- MATLAB (Simulink/Stateflow) version: >= 2020a. (MATLAB license needed)

### Installation of our tool:

- Clone the repository.
  1. `git clone https://github.com/choshina/CauMon.git`
  2. `git submodule init`
  3. `git submodule update`

- Run `make`
  
- Start MATLAB GUI,
  - set up a C/C++ compiler using the command `mex -setup`.
    > (Refer to [here](https://www.mathworks.com/help/matlab/matlab_external/changing-default-compiler.html) for more details.)
  - navigate to `breach/`, run `InitBreach`;
  - run `compile_stl_mex`;

- Run examples, such as `Figure2a.m`

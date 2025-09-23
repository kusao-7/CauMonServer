/**
 * @file stl_causation_opt.cpp
 * @brief MEX interface for optimized STL causation monitoring
 * 
 * This file provides a MATLAB MEX interface for optimized online STL monitoring
 * with causation analysis. It is an optimized version of stl_causation.cpp that
 * uses the causation_opt_upper() and causation_opt_lower() methods for more
 * efficient computation of robustness bounds in causation analysis.
 * 
 * The MEX function accepts:
 * - Signal names
 * - STL formula as string
 * - Data array
 * - Time interval
 * 
 * And returns:
 * - Upper bound robustness values (optimized causation)
 * - Lower bound robustness values (optimized causation)
 * 
 * Key differences from stl_causation.cpp:
 * - Uses causation_opt_upper/lower instead of compute_qnmono_upper/lower
 * - More efficient computation for causation analysis
 * - Optimized for real-time monitoring scenarios
 * 
 * @author CPSGrader Team
 * @date 2023
 */

// include stuff
#include "mex.h"
#include <string>
#include <iostream>
#include <sstream>
#include "stl_driver.h"

using namespace std;
using namespace CPSGrader;

/**
 * @brief MEX function entry point for optimized STL causation monitoring
 * 
 * This is the main entry point for the optimized MATLAB MEX interface. It processes
 * input arguments, parses the STL formula, and computes optimized robustness bounds
 * for online monitoring with causation analysis using the most efficient algorithms.
 * 
 * @param nlhs Number of left-hand side arguments (outputs)
 * @param plhs Array of left-hand side arguments (outputs)
 * @param nrhs Number of right-hand side arguments (inputs)
 * @param prhs Array of right-hand side arguments (inputs)
 */
void mexFunction(int nlhs, mxArray *plhs[],
                 int nrhs, const mxArray *prhs[] ) {


    stringstream fcout;
    
    if (nrhs<=1)
        mexErrMsgTxt("four inputs are expected: signal names, a formula (string), data (array), time interval (array of size 2).");
    
    /* read inputs: a string and data */
    char *signal_buf = mxArrayToString(prhs[0]);
    char *stl_buf = mxArrayToString(prhs[1]);   
    string phi_st = "signal "+ string(signal_buf) + "\n" + "phi:=" + string(stl_buf);
    
    // Get data dimensions and pointers
    size_t m = mxGetM(prhs[2]);  // Number of signals
    size_t n=  mxGetN(prhs[2]);  // Number of time points
    
    double *data_in = (double *) mxGetPr(prhs[2]);  // Input data array
    double *time_in = (double *) mxGetPr(prhs[3]);  // Time interval [start, end]
    
    // Create STL driver for formula parsing and monitoring
    STLDriver stl_driver = STLDriver();
	
//     vector<double> sample;
//     for(int i = 0; i<n; i++){
//         for(int j = 0; j<m; j++) {
//             sample.push_back(data_in[j+ i*m]);
//         }
//         stl_driver.data.push_back(sample);
//         sample.clear();
//         //cout << endl;
//     }
    
    // Variables for robustness computation
    transducer * phi; 
    double rob, rob_up, rob_low;
    rob = rob_up = rob_low = 0;
     
    Signal z, z_up, z_low;
    
    // Parse the STL formula
	bool parse_success = stl_driver.parse_string(phi_st);

    // Create output arrays for MATLAB
    plhs[0] = mxCreateDoubleMatrix(1,n, mxREAL);  // Upper bound robustness (optimized)
    plhs[1] = mxCreateDoubleMatrix(1,n, mxREAL);  // Lower bound robustness (optimized)

    double *rob_up_ptr = mxGetPr(plhs[0]);
    double *rob_low_ptr = mxGetPr(plhs[1]);

    if (parse_success) {
        // Clone the parsed formula and set monitoring parameters
		phi = stl_driver.formula_map["phi"]->clone();
        phi->set_horizon(time_in[0], time_in[1]);
        phi->set_duration();  // Set duration for optimized causation analysis

        // Process each time point for optimized online monitoring
        vector<double> sample;
        double instant = 0.0;
        ///double debug = (double)(round(0.0*100)/100);
        //double debug = 0.0;
        for(int i = 0; i < n; i++){
            // Collect all signal values for current time point
            for(int j = 0; j < m; j++){
                sample.push_back(data_in[j + i*m]);
                if(j == 0){
                    instant = data_in[j + i*m];  // Store time value
                }
            }
            
            // Add current sample to trace data
            stl_driver.data.push_back(sample);
            sample.clear();
            phi->set_trace_data_ptr(stl_driver.data);

            // Compute optimized causation robustness bounds
            // These methods are more efficient than the quasi-monotonic versions
            rob_up = phi->causation_opt_upper();
            rob_low= phi->causation_opt_lower();
            
            // Store results in output arrays
            rob_up_ptr[i] = rob_up;
            rob_low_ptr[i] = rob_low;
            
            
            //cout << "results" << rob <<" " << rob_up<< " " << rob_low <<endl;
        }
        
        // Store final signal states (for debugging or further analysis)
        z =  phi->z;
        z_low = phi->z_low;
        z_up = phi->z_up;
    }
    else
    	mexErrMsgTxt("Problem parsing formula.");

    // Clean up allocated memory
    mxFree(signal_buf);
    mxFree(stl_buf);
    delete phi;
}


//

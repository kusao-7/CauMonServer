#include "stdafx.h"
#include <transducer.h>
#include <string>
#include <iostream>
#include <sstream>
#include <string>
#include <map>
#include "tools.h"
#include <exception>
#include "signal_expr.h"
#ifdef IS_PC
#include <minmax.h>
#endif

namespace CPSGrader {

	/**
	 * Computes the upper bound causation optimization for STL atomic predicate.
	 * This method evaluates the causation optimization for atomic predicates
	 * by comparing signal values at the end of the trace.
	 * 
	 * @return The upper bound causation value for the atomic predicate
	 */
	double stl_atom::causation_opt_upper(){
        // Get the size of the trace data and the last timestamp
        int size = trace_data_ptr->size();
        double b = (trace_data_ptr->at(size-1))[0];
        cau_up.reset();
        
        // Handle case where trace ends before start time
        if(double_less(b, start_time)){
            cau_up.appendSample(start_time, TOP);
        }
        // Handle case where trace extends beyond end time
        else if(double_greater(b, end_time)){
            cau_up.appendSample(start_time, TOP);
            cau_up.appendSample(end_time, TOP);
        }
        // Normal case: trace covers the evaluation interval
        else{
            cau_up.appendSample(start_time, TOP);
            
            // Add intermediate point if available
            if(size > 2){
                double b_ = (trace_data_ptr->at(size-2))[0];
                cau_up.appendSample(b_, TOP);
            }
            
            // Get the signal value and threshold for comparison
            int i = childL->get_index();
            double vL = (trace_data_ptr->at(size-1))[i];
            double vR = childR->get_value();
            
            // Compute causation value based on comparison type
            if (comp == comparator::LESSTHAN )
                cau_up.appendSample(b, vR-vL);  // For x < threshold: threshold - x
            else
                cau_up.appendSample(b, vL-vR);  // For x > threshold: x - threshold
        }

        return cau_up.front().value;
    }

    /**
     * Computes the lower bound causation optimization for STL atomic predicate.
     * This method evaluates the causation optimization for atomic predicates
     * by comparing signal values at the end of the trace.
     * 
     * @return The lower bound causation value for the atomic predicate
     */
    double stl_atom::causation_opt_lower(){
        // Get the size of the trace data and the last timestamp
        int size = trace_data_ptr->size();
        double b = (trace_data_ptr->at(size-1))[0];
        cau_low.reset();
        
        // Handle case where trace ends before start time
        if(double_less(b, start_time)){
            cau_low.appendSample(start_time, BOTTOM);
        }
        // Handle case where trace extends beyond end time
        else if(double_greater(b, end_time)){
            cau_low.appendSample(start_time, BOTTOM);
            cau_low.appendSample(end_time, BOTTOM);
        }
        // Normal case: trace covers the evaluation interval
        else{
            cau_low.appendSample(start_time, BOTTOM);
            
            // Add intermediate point if available
            if(size > 2){
                double b_ = (trace_data_ptr->at(size-2))[0];
                cau_low.appendSample(b_, BOTTOM);
            }
            
            // Get the signal value and threshold for comparison
            int i = childL->get_index();
            double vL = (trace_data_ptr->at(size-1))[i];
            double vR = childR->get_value();
            
            // Compute causation value based on comparison type
            if (comp == comparator::LESSTHAN )
                cau_low.appendSample(b, vR-vL);  // For x < threshold: threshold - x
            else
                cau_low.appendSample(b, vL-vR);  // For x > threshold: x - threshold
        }
        return cau_low.front().value;
    }
}


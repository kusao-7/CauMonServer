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

	double stl_atom::causation_opt_upper(){
        int size = trace_data_ptr->size();
        double b = (trace_data_ptr->at(size-1))[0];
        cau_up.reset();
        if(double_less(b, start_time)){
            cau_up.appendSample(start_time, TOP);
        }else if(double_greater(b, end_time)){
            cau_up.appendSample(start_time, TOP);
            cau_up.appendSample(end_time, TOP);
        }else{
            cau_up.appendSample(start_time, TOP);
            if(size > 2){
                double b_ = (trace_data_ptr->at(size-2))[0];
                cau_up.appendSample(b_, TOP);
            }
            int i = childL->get_index();
            double vL = (trace_data_ptr->at(size-1))[i];

            double vR = childR->get_value();
            if (comp == comparator::LESSTHAN )
                cau_up.appendSample(b, vR-vL);
            else
                cau_up.appendSample(b, vL-vR);
        }

        return cau_up.front().value;
    }

    double stl_atom::causation_opt_lower(){
        int size = trace_data_ptr->size();
        double b = (trace_data_ptr->at(size-1))[0];
        cau_low.reset();
        if(double_less(b, start_time)){
            cau_low.appendSample(start_time, BOTTOM);
        }else if(double_greater(b, end_time)){
            cau_low.appendSample(start_time, BOTTOM);
            cau_low.appendSample(end_time, BOTTOM);
        }else{
            cau_low.appendSample(start_time, BOTTOM);
            if(size > 2){
                double b_ = (trace_data_ptr->at(size-2))[0];
                cau_low.appendSample(b_, BOTTOM);
            }
            int i = childL->get_index();
            double vL = (trace_data_ptr->at(size-1))[i];

            double vR = childR->get_value();
            if (comp == comparator::LESSTHAN )
                cau_low.appendSample(b, vR-vL);
            else
                cau_low.appendSample(b, vL-vR);
        }
        return cau_low.front().value;
    }
}


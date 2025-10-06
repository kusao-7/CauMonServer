#include "stdafx.h"
#include <transducer.h>
#include <algorithm>
#include <math.h>

namespace CPSGrader {

	/**
	 * Computes the upper bound causation optimization for AND transducer.
	 * This method evaluates the causation optimization by computing the upper bound
	 * for both child transducers and combining them using AND logic.
	 * 
	 * @return The upper bound causation value for the AND operation
	 */
	double and_transducer::causation_opt_upper(){
        // Compute upper bound causation for both child transducers
        childL->causation_opt_upper();
        childR->causation_opt_upper();
        
        // Combine the upper bound causation values using AND logic
        cau_up.compute_and(childL->cau_up, childR->cau_up);

        // Resize the result to the appropriate time interval with TOP value as default
        cau_up.resize(start_time,cau_up.endTime,TOP);
        
        // Ensure we have at least one sample point
        if (cau_up.empty())
            cau_up.appendSample(start_time,TOP);
            
        return cau_up.front().value;
    }

    /**
     * Computes the lower bound causation optimization for AND transducer.
     * This method evaluates the causation optimization by computing the lower bound
     * for both child transducers and combining them using AND logic.
     * The result is the maximum of two possible combinations to ensure soundness.
     * 
     * @return The lower bound causation value for the AND operation
     */
    double and_transducer::causation_opt_lower(){
        // First combination: left child causation + right child robustness
        childL->causation_opt_lower();
        childR->compute_lower_rob();
        cau_lowL.compute_and(childL->cau_low, childR->z_low);
        cau_lowL.resize(start_time, min(childL->cau_low.endTime, childR->z_low.endTime),BOTTOM);
        if(cau_lowL.empty())
            cau_lowL.appendSample(start_time, BOTTOM);

        // Second combination: left child robustness + right child causation
        childL->compute_lower_rob();
        childR->causation_opt_lower();
        cau_lowR.compute_and(childL->z_low, childR->cau_low);
        cau_lowR.resize(start_time, min(childL->z_low.endTime, childR->cau_low.endTime),BOTTOM);
        if(cau_lowR.empty())
            cau_lowR.appendSample(start_time, BOTTOM);

        // Take the maximum (OR) of both combinations for soundness
        cau_low.compute_or(cau_lowL, cau_lowR);
        cau_low.resize(start_time, cau_low.endTime, BOTTOM);
        if(cau_low.empty())
            cau_low.appendSample(start_time, BOTTOM);

        return cau_low.front().value;
    }

    /**
     * Computes the upper bound causation optimization for OR transducer.
     * This method evaluates the causation optimization by computing the upper bound
     * for both child transducers and combining them using OR logic.
     * The result is the minimum of two possible combinations to ensure soundness.
     * 
     * @return The upper bound causation value for the OR operation
     */
    double or_transducer::causation_opt_upper(){
        // First combination: left child causation + right child robustness
        childL->causation_opt_upper();
        childR->compute_upper_rob();
        cau_upL.compute_or(childL->cau_up, childR->z_up);
        cau_upL.resize(start_time, min(childL->cau_up.endTime, childR->z_up.endTime), TOP);
        if(cau_upL.empty())
            cau_upL.appendSample(start_time, TOP);

        // Second combination: left child robustness + right child causation
        childL->compute_upper_rob();
        childR->causation_opt_upper();
        cau_upR.compute_or(childL->z_up, childR->cau_up);
        cau_upR.resize(start_time, min(childL->z_up.endTime, childR->cau_up.endTime), TOP);
        if(cau_upR.empty())
            cau_upR.appendSample(start_time, TOP);

        // Take the minimum (AND) of both combinations for soundness
        cau_up.compute_and(cau_upL, cau_upR);
        cau_up.resize(start_time, cau_up.endTime, TOP);
        if(cau_up.empty())
            cau_up.appendSample(start_time, TOP);

        return cau_up.front().value;
    }

    /**
     * Computes the lower bound causation optimization for OR transducer.
     * This method evaluates the causation optimization by computing the lower bound
     * for both child transducers and combining them using OR logic.
     * 
     * @return The lower bound causation value for the OR operation
     */
    double or_transducer::causation_opt_lower(){
        // Compute lower bound causation for both child transducers
        childL->causation_opt_lower();
        childR->causation_opt_lower();
        
        // Combine the lower bound causation values using OR logic
        cau_low.compute_or(childL->cau_low, childR->cau_low);

        // Resize the result to the appropriate time interval with BOTTOM value as default
        cau_low.resize(start_time,cau_low.endTime, BOTTOM);
        if (cau_low.empty())
            cau_low.appendSample(start_time,BOTTOM);
        return cau_low.front().value;
    }

    /**
     * Computes the upper bound causation optimization for NOT transducer.
     * This method evaluates the causation optimization by computing the lower bound
     * of the child transducer and negating it.
     * 
     * @return The upper bound causation value for the NOT operation
     */
    double not_transducer::causation_opt_upper(){
        // Compute lower bound causation for the child transducer
        child->causation_opt_lower();
        
        // If child has no lower bound causation, NOT gives TOP
        if (child->cau_low.empty()) {
            cau_up.appendSample(start_time,TOP);
            return TOP;
        }
        
        // Negate the lower bound causation to get upper bound causation
        cau_up.compute_not(child->cau_low);
        return cau_up.front().value;
    }

    /**
     * Computes the lower bound causation optimization for NOT transducer.
     * This method evaluates the causation optimization by computing the upper bound
     * of the child transducer and negating it.
     * 
     * @return The lower bound causation value for the NOT operation
     */
    double not_transducer::causation_opt_lower(){
        // Compute upper bound causation for the child transducer
        child->causation_opt_upper();
        
        // If child has no upper bound causation, NOT gives BOTTOM
        if (child->cau_up.empty()) {
            cau_low.appendSample(start_time,BOTTOM);
            return BOTTOM;
        }
        
        // Negate the upper bound causation to get lower bound causation
        cau_low.compute_not(child->cau_up);
        return cau_low.front().value;
    }

    /**
     * Computes the upper bound causation optimization for EVENTUALLY transducer.
     * This method evaluates the causation optimization for the eventually operator
     * over a time interval [a,b]. It combines causation and robustness values.
     * 
     * @return The upper bound causation value for the EVENTUALLY operation
     */
    double ev_transducer::causation_opt_upper(){
        // Get time interval parameters [a,b] for the eventually operator
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        // Compute causation upper bound for the child transducer
        child->causation_opt_upper();
        if (child->cau_up.endTime < a) {
            // If child causation ends before interval start, result is TOP
            cauM.appendSample(start_time, TOP);
        }else{
            // Compute timed globally (eventually) over the interval [a,b]
            cauM.compute_timed_globally(child->cau_up, a, b);
            double et_ =min(cauM.endTime, end_time);
            double et = (double)round(et_ * 100)/100;
            cauM.resize(start_time, max(start_time,et), 0.);

            if (cauM.empty())
                cauM.appendSample(start_time, TOP);
        }

        // Compute robustness upper bound for the child transducer
        child->compute_upper_rob();
        if (child->z_up.endTime < a) {
            // If child robustness ends before interval start, result is TOP
            zM.appendSample(start_time, TOP);
        }else{
            // Compute timed eventually over the interval [a,b]
            zM.compute_timed_eventually(child->z_up, a, b);

            // Remove values computed with partial data to ensure soundness
            double et_ =min(zM.endTime-b+a,end_time);
            double et = (double)round(et_ * 100)/100;
            zM.resize(start_time,et, 0.);

            if (zM.empty())
                zM.appendSample(start_time, TOP);
        }

        // Combine causation and robustness using OR logic
        cau_up.compute_or(cauM, zM);
        cau_up.resize(start_time, min(cauM.endTime, zM.endTime),TOP);
        if (cau_up.empty())
            cau_up.appendSample(start_time,TOP);

        return cau_up.front().value;
    }

	
	/**
     * Computes the lower bound causation optimization for EVENTUALLY transducer.
     * This method evaluates the causation optimization for the eventually operator
     * over a time interval [a,b].
     * 
     * @return The lower bound causation value for the EVENTUALLY operation
     */
	double ev_transducer::causation_opt_lower(){
        // Get time interval parameters [a,b] for the eventually operator
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        // Compute causation lower bound for the child transducer
        child->causation_opt_lower();
        if (child->cau_low.endTime < a) {
            // If child causation ends before interval start, result is BOTTOM
            cau_low.appendSample(start_time, BOTTOM);
            return BOTTOM;
        }

        // Compute timed eventually over the interval [a,b]
        cau_low.compute_timed_eventually(child->cau_low, a, b);
        double et_ =min(cau_low.endTime, end_time);
        double et = (double)round(et_ * 100)/100;
        cau_low.resize(start_time, max(start_time,et), 0.);

        if (cau_low.empty())
            cau_low.appendSample(start_time, BOTTOM);

        return cau_low.front().value;
    }

    /**
     * Computes the upper bound causation optimization for ALWAYS transducer.
     * This method evaluates the causation optimization for the always operator
     * over a time interval [a,b].
     * 
     * @return The upper bound causation value for the ALWAYS operation
     */
    double alw_transducer::causation_opt_upper(){
        // Get time interval parameters [a,b] for the always operator
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        // Compute causation upper bound for the child transducer
        child->causation_opt_upper();
        if (child->cau_up.endTime < a) {
            // If child causation ends before interval start, result is TOP
            cau_up.appendSample(start_time, TOP);
            return TOP;
        }
        
        // Compute timed globally (always) over the interval [a,b]
        cau_up.compute_timed_globally(child->cau_up, a, b);
        double et_ =min(cau_up.endTime, end_time);
        double et = (double)round(et_ * 100)/100;
        cau_up.resize(start_time, max(start_time,et), 0.);

        if (cau_up.empty())
            cau_up.appendSample(start_time, TOP);

        return cau_up.front().value;

    }

    /**
     * Computes the lower bound causation optimization for ALWAYS transducer.
     * This method evaluates the causation optimization for the always operator
     * over a time interval [a,b]. It combines causation and robustness values.
     * 
     * @return The lower bound causation value for the ALWAYS operation
     */
    double alw_transducer::causation_opt_lower(){
        // Get time interval parameters [a,b] for the always operator
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        // Compute causation lower bound for the child transducer
        child->causation_opt_lower();
        if (child->cau_low.endTime < a) {
            // If child causation ends before interval start, result is BOTTOM
            cauM.appendSample(start_time, BOTTOM);
        }else{
            // Compute timed eventually over the interval [a,b]
            cauM.compute_timed_eventually(child->cau_low, a, b);
            double et_ =min(cauM.endTime, end_time);
            double et = (double)round(et_ * 100)/100;
            cauM.resize(start_time, max(start_time,et), 0.);

            if (cauM.empty())
                cauM.appendSample(start_time, BOTTOM);
        }

        // Compute robustness lower bound for the child transducer
        child->compute_lower_rob();
        if (child->z_low.endTime < a) {
            // If child robustness ends before interval start, result is BOTTOM
            zM.appendSample(start_time, BOTTOM);
        }else{
            // Compute timed globally over the interval [a,b]
            zM.compute_timed_globally(child->z_low, a, b);

            // Remove values computed with partial data to ensure soundness
            double et_ =min(zM.endTime-b+a, end_time);
            double et = (double)round(et_ * 100)/100;
            zM.resize(start_time, et, 0.);

            if (zM.empty())
                zM.appendSample(start_time, BOTTOM);
        }
        
        // Combine causation and robustness using AND logic
        cau_low.compute_and(cauM, zM);
        cau_low.resize(start_time, min(cauM.endTime, zM.endTime), BOTTOM);
        if (cau_low.empty())
            cau_low.appendSample(start_time, BOTTOM);

        return cau_low.front().value;

    }

}


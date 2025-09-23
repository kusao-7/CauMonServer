#include "stdafx.h"
#include <transducer.h>
#include <algorithm>
#include <math.h>

namespace CPSGrader {

	double and_transducer::causation_opt_upper(){
        childL->causation_opt_upper();
        childR->causation_opt_upper();
        cau_up.compute_and(childL->cau_up, childR->cau_up);

        cau_up.resize(start_time,cau_up.endTime,TOP);
        if (cau_up.empty())
            cau_up.appendSample(start_time,TOP);
        return cau_up.front().value;
    }

    double and_transducer::causation_opt_lower(){
        childL->causation_opt_lower();
        childR->compute_lower_rob();
        cau_lowL.compute_and(childL->cau_low, childR->z_low);
        cau_lowL.resize(start_time, min(childL->cau_low.endTime, childR->z_low.endTime),BOTTOM);
        if(cau_lowL.empty())
            cau_lowL.appendSample(start_time, BOTTOM);

        childL->compute_lower_rob();
        childR->causation_opt_lower();
        cau_lowR.compute_and(childL->z_low, childR->cau_low);
        cau_lowR.resize(start_time, min(childL->z_low.endTime, childR->cau_low.endTime),BOTTOM);
        if(cau_lowR.empty())
            cau_lowR.appendSample(start_time, BOTTOM);

        cau_low.compute_or(cau_lowL, cau_lowR);
        cau_low.resize(start_time, cau_low.endTime, BOTTOM);
        if(cau_low.empty())
            cau_low.appendSample(start_time, BOTTOM);

        return cau_low.front().value;
    }

    double or_transducer::causation_opt_upper(){
        childL->causation_opt_upper();
        childR->compute_upper_rob();
        cau_upL.compute_or(childL->cau_up, childR->z_up);
        cau_upL.resize(start_time, min(childL->cau_up.endTime, childR->z_up.endTime), TOP);
        if(cau_upL.empty())
            cau_upL.appendSample(start_time, TOP);

        childL->compute_upper_rob();
        childR->causation_opt_upper();
        cau_upR.compute_or(childL->z_up, childR->cau_up);
        cau_upR.resize(start_time, min(childL->z_up.endTime, childR->cau_up.endTime), TOP);
        if(cau_upR.empty())
            cau_upR.appendSample(start_time, TOP);

        cau_up.compute_and(cau_upL, cau_upR);
        cau_up.resize(start_time, cau_up.endTime, TOP);
        if(cau_up.empty())
            cau_up.appendSample(start_time, TOP);

        return cau_up.front().value;
    }

    double or_transducer::causation_opt_lower(){
        childL->causation_opt_lower();
        childR->causation_opt_lower();
        cau_low.compute_or(childL->cau_low, childR->cau_low);

        cau_low.resize(start_time,cau_low.endTime, BOTTOM);
        if (cau_low.empty())
            cau_low.appendSample(start_time,BOTTOM);
        return cau_low.front().value;
    }

    double not_transducer::causation_opt_upper(){
        child->causation_opt_lower();
        if (child->cau_low.empty()) {
            cau_up.appendSample(start_time,TOP);
            return TOP;
        }
        cau_up.compute_not(child->cau_low);
        return cau_up.front().value;
    }

    double not_transducer::causation_opt_lower(){
        child->causation_opt_upper();
        if (child->cau_up.empty()) {
            cau_low.appendSample(start_time,BOTTOM);
            return BOTTOM;
        }
        cau_low.compute_not(child->cau_up);
        return cau_low.front().value;
    }

    double ev_transducer::causation_opt_upper(){
        //ZHENYA
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        child->causation_opt_upper();
        if (child->cau_up.endTime < a) {
            cauM.appendSample(start_time, TOP);
        }else{
            cauM.compute_timed_globally(child->cau_up, a, b);
            double et_ =min(cauM.endTime, end_time);
            double et = (double)round(et_ * 100)/100;
            cauM.resize(start_time, max(start_time,et), 0.);

            if (cauM.empty())
                cauM.appendSample(start_time, TOP);
        }

        child->compute_upper_rob();
        if (child->z_up.endTime < a) {
            zM.appendSample(start_time, TOP);
        }else{
            zM.compute_timed_eventually(child->z_up, a, b);

            // Here we remove values computed with partial data 
            double et_ =min(zM.endTime-b+a,end_time);
            double et = (double)round(et_ * 100)/100;
            zM.resize(start_time,et, 0.);

            if (zM.empty())
                zM.appendSample(start_time, TOP);
        }

        cau_up.compute_or(cauM, zM);
        cau_up.resize(start_time, min(cauM.endTime, zM.endTime),TOP);
        if (cau_up.empty())
            cau_up.appendSample(start_time,TOP);

        return cau_up.front().value;
    }

	
	double ev_transducer::causation_opt_lower(){
        //ZHENYA
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        child->causation_opt_lower();
        if (child->cau_low.endTime < a) {
            cau_low.appendSample(start_time, BOTTOM);
            return BOTTOM;
        }

        cau_low.compute_timed_eventually(child->cau_low, a, b);
        double et_ =min(cau_low.endTime, end_time);
        double et = (double)round(et_ * 100)/100;
        cau_low.resize(start_time, max(start_time,et), 0.);

        if (cau_low.empty())
            cau_low.appendSample(start_time, BOTTOM);

        return cau_low.front().value;
    }

    double alw_transducer::causation_opt_upper(){
        //ZHENYA
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        child->causation_opt_upper();
        if (child->cau_up.endTime < a) {
            cau_up.appendSample(start_time, TOP);
            return TOP;
        }
        cau_up.compute_timed_globally(child->cau_up, a, b);
        double et_ =min(cau_up.endTime, end_time);
        double et = (double)round(et_ * 100)/100;
        cau_up.resize(start_time, max(start_time,et), 0.);

        if (cau_up.empty())
            cau_up.appendSample(start_time, TOP);

        return cau_up.front().value;

    }

    double alw_transducer::causation_opt_lower(){
        double a,b;
        if (!get_param(I->begin_str,a)) a = I->begin;
        if (!get_param(I->end_str,b)) b = I->end;

        child->causation_opt_lower();
        if (child->cau_low.endTime < a) {
            cauM.appendSample(start_time, BOTTOM);
        }else{
            cauM.compute_timed_eventually(child->cau_low, a, b);
            double et_ =min(cauM.endTime, end_time);
            double et = (double)round(et_ * 100)/100;
            cauM.resize(start_time, max(start_time,et), 0.);

            if (cauM.empty())
                cauM.appendSample(start_time, BOTTOM);
        }

        child->compute_lower_rob();
        if (child->z_low.endTime < a) {
            zM.appendSample(start_time, BOTTOM);
        }else{
            zM.compute_timed_globally(child->z_low, a, b);

            double et_ =min(zM.endTime-b+a, end_time);
            double et = (double)round(et_ * 100)/100;
            zM.resize(start_time, et, 0.);

            if (zM.empty())
                zM.appendSample(start_time, BOTTOM);
        }
        cau_low.compute_and(cauM, zM);
        cau_low.resize(start_time, min(cauM.endTime, zM.endTime), BOTTOM);
        if (cau_low.empty())
            cau_low.appendSample(start_time, BOTTOM);

        return cau_low.front().value;

    }

}


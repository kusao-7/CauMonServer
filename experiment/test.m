signal_str = 'speed,RPM';
phi_str = 'alw_[0,27](not(speed[t]>50) or ev_[1,3](RPM[t] < 3000))';
tau = 0;
trace_file = 'data/AT_phi3_trace9.mat';
load(trace_file, 'trace');

[up_robM, low_robM] = stl_eval_mex_pw(signal_str, phi_str, trace, tau);
[up_optCau, low_optCau] = stl_causation_opt(signal_str, phi_str, trace, tau);

visualize(...
    trace, ...          % time and signals
    phi_str, ...        % formula string for title
    up_robM, low_robM, ... % robustness metrics
    up_optCau, low_optCau, ... % causation metrics
    'result.png' ... % output file
);

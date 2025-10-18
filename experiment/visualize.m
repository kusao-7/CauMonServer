function visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, outfile)
%PLOT_STL_ANALYSIS Generic plotting routine for STL analysis results
%
% trace       - 2D matrix where first row is time, following rows are signals
% phi_str     - string with STL formula
% up_robM     - upper robustness values
% low_robM    - lower robustness values
% up_optCau   - upper causation values
% low_optCau  - lower causation values
% outfile     - output filename (PNG, optional)

    if nargin < 7
        outfile = 'Figure_output.png';
    end

    t = trace(1,:);
    num_signals = size(trace,1) - 1;

    % === Figure setup ===
    total_plots = num_signals + 2; % one per signal + robustness + causation
    f = figure('Position',[100 100 900 300 + 150*total_plots]);
    tiledlayout(total_plots,1, 'Padding','compact', 'TileSpacing','compact');

    % ====== (1..N) Plot each signal separately ======
    for s = 2:(num_signals+1)
        nexttile;
        plot(t, trace(s,:), 'LineWidth', 2);
        title(sprintf('Signal %d', s-1), 'FontWeight','bold');
        xlabel('Time');
        ylabel('Value');
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14);
    end

    % ====== (N+1) Robustness ======
    nexttile;
    hold on;
    stairs(t(2:end), up_robM(2:end), 'LineWidth', 2);
    stairs(t(2:end), low_robM(2:end), 'LineWidth', 2);
    legend({'Upper robustness','Lower robustness'}, 'Location','best');
    xlabel('Time');
    ylabel('Robustness');
    title('STL Robustness');
    grid on;
    set(gca, 'LineWidth', 1.5, 'FontSize', 14);

    % ====== (N+2) Causation ======
    nexttile;
    hold on;
    stairs(t(2:end), up_optCau(2:end), 'LineWidth', 2);
    stairs(t(2:end), low_optCau(2:end), 'LineWidth', 2);
    legend({'Violation causation','Satisfaction causation'}, 'Location','best');
    xlabel('Time');
    ylabel('Causation');
    title('STL Causation');
    grid on;
    set(gca, 'LineWidth', 1.5, 'FontSize', 14);

    % ====== Global title ======
    sgtitle(strrep(phi_str, '_', '\_'), 'Interpreter', 'none', 'FontSize', 16, 'FontWeight', 'bold');

    % ====== Save Figure ======
    if ~isempty(outfile)
        exportgraphics(f, outfile, 'Resolution', 300);
    end
end

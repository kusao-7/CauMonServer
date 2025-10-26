function visualize(trace, phi_str, up_robM, low_robM, up_optCau, low_optCau, signal_names_str, outfile)
%PLOT_STL_ANALYSIS Generic plotting routine for STL analysis results
%
% [動的更新バージョン]
% 1つのFigureウィンドウを再利用し、プロットデータのみを更新する。

    % --- 引数のデフォルト値設定 ---
    if nargin < 8
        outfile = 'Figure_output.png'; % 保存ファイル名（毎回上書き）
    end

    % --- データ抽出 (時間とシグナル数) ---
    t = trace(1,:);
    num_signals = size(trace,1) - 1;

    % --- 永続的なFigureハンドルを検索 ---
    % 'CauMon_Realtime_Monitor_Figure' という名前のタグで既存のFigureを探す
    figTag = 'CauMon_Realtime_Monitor_Figure';
    figHandle = findobj('Tag', figTag);

    if isempty(figHandle)
        % --- 初回呼び出し (Figureが存在しない場合) ---
        % グラフを新規作成し、プロットのハンドルを保存する。

        total_plots = num_signals + 2; % シグナル数 + ロバストネス + 因果関係

        % ★ Figureに 'Tag' を追加して作成
        figHandle = figure('Tag', figTag, 'Position',[100 100 900 300 + 150*total_plots]);

        tiledlayout(total_plots,1, 'Padding','compact', 'TileSpacing','compact');

        % ハンドルを保存するための構造体
        handles = struct();

        % ====== (1..N) 各シグナルをプロット ======
        handles.h_sig = gobjects(num_signals, 1); % シグナルプロット用ハンドル配列
        handles.ax_sig = gobjects(num_signals, 1); % シグナル軸用ハンドル配列

        % 信号名文字列をコンマで分割
        signal_names_cell = strsplit(signal_names_str, ',');

        for s = 1:num_signals
            handles.ax_sig(s) = nexttile; % ★ 軸ハンドルを保存

            % ★ プロットハンドルを h_sig に保存 (インデックスは s)
            handles.h_sig(s) = plot(t, trace(s+1,:), 'LineWidth', 2); % s+1 行目がシグナルデータ

            % (↓ 元のコードと同じスタイリング)
            current_signal_name = signal_names_cell{s};
            title(sprintf('Signal %d', s), 'FontWeight','bold'); % s-1 から s に変更
            xlabel('Time');
            ylabel(current_signal_name);
            grid on;
            set(gca, 'LineWidth', 1.5, 'FontSize', 14);
        end

        % ====== (N+1) ロバストネスをプロット ======
        handles.ax_rob = nexttile; % ★ 軸ハンドルを保存
        hold on;

        % ★ プロットハンドルを保存
        handles.h_rob_up = stairs(t(2:end), up_robM(2:end), 'LineWidth', 2);
        handles.h_rob_low = stairs(t(2:end), low_robM(2:end), 'LineWidth', 2);

        % (↓ 元のコードと同じスタイリング)
        legend({'Upper robustness','Lower robustness'}, 'Location','best');
        xlabel('Time');
        ylabel('Robustness');
        title('STL Robustness');
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14);
        hold off;

        % ====== (N+2) 因果関係をプロット ======
        handles.ax_cau = nexttile; % ★ 軸ハンドルを保存
        hold on;

        % ★ プロットハンドルを保存
        handles.h_cau_up = stairs(t(2:end), up_optCau(2:end), 'LineWidth', 2);
        handles.h_cau_low = stairs(t(2:end), low_optCau(2:end), 'LineWidth', 2);

        % (↓ 元のコードと同じスタイリング)
        legend({'Violation causation','Satisfaction causation'}, 'Location','best');
        xlabel('Time');
        ylabel('Causation');
        title('STL Causation');
        grid on;
        set(gca, 'LineWidth', 1.5, 'FontSize', 14);
        hold off;

        % ====== 全体のタイトル ======
        sgtitle(strrep(phi_str, '_', '\_'), 'Interpreter', 'none', 'FontSize', 16, 'FontWeight', 'bold');

        % ★ 作成したすべてのハンドルをFigureに保存する
        setappdata(figHandle, 'PlotHandles', handles);

    else
        % --- 2回目以降の呼び出し (Figureが存在する場合) ---
        % グラフを再描画せず、データ (XData, YData) のみを更新する

        % 既存のFigureを一番手前に持ってくる (ウィンドウが後ろにいかないように)
        figure(figHandle);

        % ★ Figureに保存したハンドル構造体を取得
        handles = getappdata(figHandle, 'PlotHandles');

        % ====== (1..N) Signals データを更新 ======
        for s = 1:num_signals
            % ハンドルが存在するか確認 (念のため)
            if isgraphics(handles.h_sig(s))
                set(handles.h_sig(s), 'XData', t, 'YData', trace(s+1,:));
                % 軸の自動スケール (Y軸のみ更新、X軸は伸びるように)
                set(handles.ax_sig(s), 'XLimMode', 'auto', 'YLimMode', 'auto');
            else
                warning('Signal plot handle %d is invalid.', s);
            end
        end

        % ====== (N+1) Robustness データを更新 ======
         if isgraphics(handles.h_rob_up) && isgraphics(handles.h_rob_low)
            set(handles.h_rob_up, 'XData', t(2:end), 'YData', up_robM(2:end));
            set(handles.h_rob_low, 'XData', t(2:end), 'YData', low_robM(2:end));
            set(handles.ax_rob, 'XLimMode', 'auto', 'YLimMode', 'auto');
         else
             warning('Robustness plot handle is invalid.');
         end

        % ====== (N+2) Causation データを更新 ======
        if isgraphics(handles.h_cau_up) && isgraphics(handles.h_cau_low)
            set(handles.h_cau_up, 'XData', t(2:end), 'YData', up_optCau(2:end));
            set(handles.h_cau_low, 'XData', t(2:end), 'YData', low_optCau(2:end));
            set(handles.ax_cau, 'XLimMode', 'auto', 'YLimMode', 'auto');
        else
             warning('Causation plot handle is invalid.');
        end

        % ★ 変更を画面に反映
        drawnow limitrate; % limitrate オプションで更新頻度を調整
    end

    % ====== Save Figure (毎回上書き保存) ======
    if ~isempty(outfile) && isgraphics(figHandle)
        try
            exportgraphics(figHandle, outfile, 'Resolution', 300);
        catch ME
            warning('Failed to save figure to %s: %s', outfile, ME.message);
        end
    end
end
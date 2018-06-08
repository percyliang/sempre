#!/usr/bin/env ruby

# Verifies that the codebase is sane (compiles, doesn't crash, gets reasonable
# accuracy) every once in a while.  If something fails, an email is sent out

$mode = ARGV[0]
if $mode != 'now' && $mode != 'loop'
  puts "Usage: #{$0} (now|loop)"
  puts "  now: check immediately and exit (don't email)"
  puts "  loop: check only when stuff changes and loop (email if something breaks)"
  exit 1
end

# Who should be notified if the code breaks.
$recipient = 'stanford-sempre@googlegroups.com'
$logPath = "verify-code-loop.log"

# Send out the log file to all the recipients.
def emailLog(subject)
  return unless $mode == 'loop'

  maxLines = 100  # Maximum number of lines to send via email.
  numLines = IO.readlines($logPath).size
  if numLines <= maxLines
    command = "cat #{$logPath}"
  else
    # Take first few lines and last few lines to keep under maxLines.
    command = "(head -#{maxLines/2} #{$logPath}; echo '... (#{numLines - maxLines} lines omitted) ...'; tail -#{maxLines/2} #{$logPath})"
  end
  command = "#{command} | mail -s '#{subject}' #{$recipient}"
  puts "Emailing log file: #{command}"
  system command or exit 1
end

def emailBroken
  emailLog('sempre code is broken!')
end

# Print to stdout and log file.
def log(line, newline=true)
  line = "[#{`date`.chomp}] #{line}"
  if newline
    puts line
  else
    print line
  end
  out = open($logPath, 'a')
  out.puts line
  out.close
end

# Run and command; if fail, send email.
def run(command, verbose)
  log("======== Running: #{command}", false) if verbose
  ok = system "#{command} >> #{$logPath} 2>&1"
  puts " [#{ok ? 'ok' : 'failed'}]" if verbose
  emailBroken if not ok
  ok
end

def restart
  exit 1 if $mode == 'now'

  system "cat #{$logPath}"

  # In case there are updates
  log("Restarting #{$0}...")
  exec($0 + ' loop')
end

log("Started verify-code loop version 2")
log("Writing to #{$logPath}...")
firstTime = true
while true
  break if $mode == 'now' && (not firstTime)

  # Whenever there's a change to the repository, run a test
  system "rm -f #{$logPath}"
  if not run('git pull', false)
    log("git pull failed - this is bad, let's just quit.")
    break
  end

  if $mode == 'loop' && system("grep -q 'Already up-to-date' #{$logPath}")
    # No changes, just wait
    sleep 60
    next
  end
  firstTime = false

  # Check everything
  log("Testing...")
  run('git log -3', true) or restart # Print out last commit messages
  run('./pull-dependencies', true) or restart
  run('ant clean', true) or restart
  run('ant', true) or restart

  run('scripts/find-hard-coded-paths.rb', true) or restart

  # Run tests
  run("./run @mode=test", true) or restart

  emailLog('sempre code passes tests!')

  break if $mode == 'now'
  restart
end
